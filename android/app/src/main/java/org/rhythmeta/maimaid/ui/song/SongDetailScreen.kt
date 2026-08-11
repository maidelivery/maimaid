package org.rhythmeta.maimaid.ui.song

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.ScoreInput
import org.rhythmeta.maimaid.core.data.ScoreRules
import org.rhythmeta.maimaid.core.data.ScoreValidationError
import org.rhythmeta.maimaid.core.database.PlayRecordEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor
import java.math.RoundingMode
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private data class MetadataItem(
    val value: String,
    val icon: ImageVector,
    val label: String,
)

private fun copyToClipboard(
    context: Context,
    label: String,
    value: String,
): Boolean {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    return true
}

private const val ClipboardSnackbarDurationMillis = 3_000L
private val LockRequiredLightColor = Color(0xFFB45F62)
private val LockRequiredDarkColor = Color(0xFFD98B8B)
private val LockNotRequiredLightColor = Color(0xFF5F916B)
private val LockNotRequiredDarkColor = Color(0xFF8FBC98)

@Composable
fun SongDetailScreen(
    song: SongEntity?,
    container: AppContainer,
    contentTopPadding: androidx.compose.ui.unit.Dp,
    onBackgroundChanged: (Color?) -> Unit,
    onTitleChanged: (String) -> Unit,
) {
    if (song == null) {
        EmptySongState()
        return
    }

    val viewModel: SongDetailViewModel = viewModel(
        key = "song-detail-${song.songIdentifier}",
        factory = SongDetailViewModel.Factory(song.songIdentifier, container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val aliases by container.catalogRepository
        .observeAliasesForSong(song.songIdentifier)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val copiedConfirmation = stringResource(R.string.common_copied_to_clipboard)
    val showCopiedSnackbar: () -> Unit = {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = copiedConfirmation,
                duration = SnackbarDuration.Custom(ClipboardSnackbarDurationMillis),
            )
        }
    }
    var recordToDelete by remember { mutableStateOf<PlayRecordEntity?>(null) }
    var jacketColor by remember(song.songIdentifier) { mutableStateOf<Color?>(null) }
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val detailColors = jacketColor?.let { SongVisualUtils.detailColors(it, darkTheme) }
    val surfaceColor = detailColors?.surface ?: MiuixTheme.colorScheme.surfaceContainer
    val selectedSurfaceColor = detailColors?.selectedSurface ?: MiuixTheme.colorScheme.secondaryContainer
    val accentColor = detailColors?.accent ?: MiuixTheme.colorScheme.primary
    val cachedCover = remember(song.imageName) { container.coverImageStore.fileFor(song.imageName) }
    val chartTypes = state.charts.map { it.sheet.type.displayChartType() }.distinct()
    var selectedType by rememberSaveable(song.songIdentifier) { mutableStateOf<String?>(null) }
    LaunchedEffect(chartTypes) {
        if (selectedType == null && chartTypes.isNotEmpty()) {
            selectedType = chartTypes.firstOrNull { it == "DX" }
                ?: chartTypes.firstOrNull { it == "STD" }
                ?: chartTypes.first()
        }
    }
    val visibleCharts = state.charts.filter { chart ->
        selectedType == null || chart.sheet.type.displayChartType() == selectedType
    }
    val selectedChartVersion = visibleCharts.firstNotNullOfOrNull { chart ->
        chart.sheet.version?.trim()?.takeIf(String::isNotEmpty)
    } ?: song.version?.trim()?.takeIf(String::isNotEmpty)
    val selectedProviderSongId = visibleCharts
        .firstOrNull { it.sheet.providerSongId > 0 }
        ?.sheet
        ?.providerSongId
    LaunchedEffect(detailColors) {
        onBackgroundChanged(detailColors?.background)
    }
    LaunchedEffect(song.songIdentifier, selectedProviderSongId, song.title) {
        onTitleChanged(
            selectedProviderSongId?.let { "#$it" }
                ?: song.title,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(detailColors?.background ?: MiuixTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = contentTopPadding + 8.dp,
                end = 16.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SongHeader(
                    song = song,
                    aliases = aliases,
                    surfaceColor = surfaceColor,
                    accentColor = accentColor,
                    cachedCover = cachedCover,
                    metadataVersion = selectedChartVersion,
                    onJacketColor = { jacketColor = it },
                    onCopied = showCopiedSnackbar,
                )
            }
            item { CommunityAliasSection(aliases, surfaceColor, accentColor) }
            item { RegionAvailability(state.charts.map { it.sheet }, song.isLocked, surfaceColor, accentColor) }
            item { ExternalSearch(song.title, surfaceColor, accentColor) }
            if (chartTypes.isNotEmpty()) {
                item {
                    ChartTypeSelector(
                        types = chartTypes,
                        selected = selectedType,
                        surfaceColor = surfaceColor,
                        selectedSurfaceColor = selectedSurfaceColor,
                        onSelected = { selectedType = it },
                    )
                }
            }
            if (state.charts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.song_no_charts),
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(
                    count = visibleCharts.size,
                    key = { index -> visibleCharts[index].sheet.sheetKey },
                ) { index ->
                    val chart = visibleCharts[index]
                    SheetScoreCard(
                        chart = chart,
                        surfaceColor = surfaceColor,
                        actionSurfaceColor = selectedSurfaceColor,
                        accentColor = accentColor,
                        onRecord = { viewModel.openScoreEntry(chart.sheet.sheetKey) },
                        onDeleteRecord = { recordToDelete = it },
                    )
                }
            }
        }
        SnackbarHost(
            state = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        )
    }

    val entryChart = state.entrySheetKey?.let { key ->
        state.charts.firstOrNull { it.sheet.sheetKey == key }
    }
    if (entryChart != null) {
        ScoreEntryDialog(
            chart = entryChart,
            saveStatus = state.saveStatus,
            accentColor = accentColor,
            onInputChanged = viewModel::markEntryChanged,
            onSave = viewModel::saveScore,
            onDismiss = viewModel::dismissScoreEntry,
        )
    }

    recordToDelete?.let { record ->
        DeleteRecordDialog(
            onConfirm = {
                viewModel.deletePlayRecord(record.id)
                recordToDelete = null
            },
            onDismiss = { recordToDelete = null },
        )
    }
}

@Composable
private fun SongHeader(
    song: SongEntity,
    aliases: List<String>,
    surfaceColor: Color,
    accentColor: Color,
    cachedCover: File?,
    metadataVersion: String?,
    onJacketColor: (Color) -> Unit,
    onCopied: () -> Unit,
) {
    val context = LocalContext.current
    val artist = song.artist.ifBlank { stringResource(R.string.song_artist_unknown) }
    val titleInteractionSource = remember { MutableInteractionSource() }
    val artistInteractionSource = remember { MutableInteractionSource() }
    val jacketModel = remember(cachedCover, song.imageName) {
        ImageRequest.Builder(context)
            .data(cachedCover ?: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover/${song.imageName}")
            .allowHardware(false)
            .build()
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .squircleSurface(
                    color = MiuixTheme.colorScheme.surfaceContainer,
                    cornerRadius = 26.dp,
                    extension = SquircleExtension,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.Edit,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(42.dp),
            )
            AsyncImage(
                model = jacketModel,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { result ->
                    val averageColor = runCatching {
                        val bitmap = result.result.drawable.toBitmap(config = Bitmap.Config.ARGB_8888)
                        SongVisualUtils.averageJacketColor(bitmap)
                    }.getOrNull()
                    averageColor?.let(onJacketColor)
                },
            )
        }
        Spacer(Modifier.height(14.dp))
        MarqueeText(
            text = song.title,
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(
                interactionSource = titleInteractionSource,
                indication = null,
                onClick = {
                    if (copyToClipboard(context, song.title, song.title)) onCopied()
                },
            ),
        )
        MarqueeText(
            text = artist,
            style = MiuixTheme.textStyles.body1,
            color = accentColor,
            modifier = Modifier.clickable(
                interactionSource = artistInteractionSource,
                indication = null,
                onClick = {
                    if (copyToClipboard(context, artist, artist)) onCopied()
                },
            ),
        )
        if (aliases.isNotEmpty()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .requiredWidth(maxWidth + 16.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    aliases.forEach { alias ->
                        Text(
                            text = alias,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .squircleSurface(
                                    color = surfaceColor,
                                    cornerRadius = 50.dp,
                                    extension = SquircleExtension,
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        MetadataGrid(
            listOfNotNull(
                song.bpm?.let {
                    MetadataItem(it.toInt().toString(), MiuixIcons.Timer, stringResource(R.string.song_bpm))
                },
                song.category.ifBlank { "maimai" }.let {
                    MetadataItem(it, MiuixIcons.GridView, stringResource(R.string.song_category))
                },
                metadataVersion?.takeIf(String::isNotBlank)?.let {
                    MetadataItem(
                        SongVisualUtils.formatVersionName(it),
                        MiuixIcons.Album,
                        stringResource(R.string.song_version),
                    )
                },
                song.releaseDate?.takeIf(String::isNotBlank)?.let {
                    MetadataItem(it, MiuixIcons.Months, stringResource(R.string.song_release_date))
                },
            ),
            surfaceColor = surfaceColor,
            accentColor = accentColor,
            onItemClick = { item ->
                if (copyToClipboard(context, item.label, item.value)) onCopied()
            },
        )
    }
}

@Composable
private fun MetadataGrid(
    values: List<MetadataItem>,
    surfaceColor: Color,
    accentColor: Color,
    onItemClick: (MetadataItem) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val singleRow = maxWidth >= 520.dp
        if (singleRow) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEach {
                    MetadataChip(it, Modifier.weight(1f), surfaceColor, accentColor) {
                        onItemClick(it)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                values.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach {
                            MetadataChip(it, Modifier.weight(1f), surfaceColor, accentColor) {
                                onItemClick(it)
                            }
                        }
                        repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarqueeText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    fontWeight: FontWeight? = null,
    color: Color = MiuixTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = style,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .basicMarquee(),
    )
}

@Composable
private fun CommunityAliasSection(
    aliases: List<String>,
    surfaceColor: Color,
    accentColor: Color,
) {
    SongDetailCard(color = surfaceColor) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.song_aliases_title),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (aliases.isEmpty()) {
                        stringResource(R.string.song_aliases_empty)
                    } else {
                        stringResource(R.string.song_aliases_count, aliases.size)
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Text(
                text = stringResource(R.string.song_aliases_board),
                style = MiuixTheme.textStyles.footnote1,
                color = accentColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RegionAvailability(
    charts: List<SheetEntity>,
    isLocked: Boolean,
    surfaceColor: Color,
    accentColor: Color,
) {
    val jp = charts.any(SheetEntity::regionJp)
    val intl = charts.any(SheetEntity::regionIntl)
    val cn = charts.any(SheetEntity::regionCn)
    SongDetailCard(color = surfaceColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RegionFlag("🇯🇵", stringResource(R.string.song_region_japan), jp, accentColor)
            RegionFlag("🌏", stringResource(R.string.song_region_international), intl, accentColor)
            RegionFlag("🇨🇳", stringResource(R.string.song_region_china), cn, accentColor)
            Spacer(Modifier.weight(1f))
            val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
            val availabilityIconColor = if (isLocked) {
                MiuixTheme.colorScheme.onBackgroundVariant
            } else {
                accentColor
            }
            val availabilityTextColor = if (isLocked) {
                if (darkTheme) LockRequiredDarkColor else LockRequiredLightColor
            } else {
                if (darkTheme) LockNotRequiredDarkColor else LockNotRequiredLightColor
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = if (isLocked) MiuixIcons.Lock else MiuixIcons.Unlock,
                    contentDescription = null,
                    tint = availabilityIconColor,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(if (isLocked) R.string.song_lock_required else R.string.song_lock_not_required),
                    style = MiuixTheme.textStyles.footnote2,
                    color = availabilityTextColor,
                )
            }
        }
    }
}

@Composable
private fun RegionFlag(flag: String, label: String, available: Boolean, accentColor: Color) {
    val contentColor = if (available) accentColor else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = flag,
            style = MiuixTheme.textStyles.title3,
            modifier = Modifier.alpha(if (available) 1f else 0.32f),
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = contentColor,
        )
    }
}

@Composable
private fun ExternalSearch(title: String, surfaceColor: Color, accentColor: Color) {
    val uriHandler = LocalUriHandler.current
    val encodedTitle = android.net.Uri.encode(title)
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val brandColors = SongVisualUtils.externalSearchColors(darkTheme)
    SongDetailCard(color = surfaceColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = MiuixIcons.Search,
                contentDescription = stringResource(R.string.song_external_search),
                tint = accentColor,
                modifier = Modifier.size(20.dp),
            )
            SongDetailTextButton(
                text = stringResource(R.string.song_external_youtube),
                onClick = {
                    uriHandler.openUri("https://www.youtube.com/results?search_query=maimai+$encodedTitle")
                },
                modifier = Modifier.weight(1f),
                surfaceColor = brandColors.youtubeSurface,
                icon = MiuixIcons.Play,
                textStyle = MiuixTheme.textStyles.footnote1,
                contentColor = brandColors.youtubeContent,
            )
            SongDetailTextButton(
                text = stringResource(R.string.song_external_bilibili),
                onClick = {
                    uriHandler.openUri("https://search.bilibili.com/all?keyword=maimai+$encodedTitle")
                },
                modifier = Modifier.weight(1f),
                surfaceColor = brandColors.bilibiliSurface,
                icon = MiuixIcons.Music,
                textStyle = MiuixTheme.textStyles.footnote1,
                contentColor = brandColors.bilibiliContent,
            )
        }
    }
}

@Composable
private fun ChartTypeSelector(
    types: List<String>,
    selected: String?,
    surfaceColor: Color,
    selectedSurfaceColor: Color,
    onSelected: (String?) -> Unit,
) {
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { type ->
            val isSelected = selected == type
            val typeColor = SongVisualUtils.chartTypeColor(
                type = type,
                darkTheme = darkTheme,
                fallbackColor = MiuixTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .squircleSurface(
                        color = if (isSelected) selectedSurfaceColor else surfaceColor,
                        cornerRadius = 12.dp,
                        extension = SquircleExtension,
                    )
                    .clickable(
                        enabled = types.size > 1,
                        onClick = { onSelected(type) },
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = if (types.size == 1) Arrangement.Start else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(typeColor),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = type,
                    style = MiuixTheme.textStyles.button,
                    color = if (isSelected) typeColor else MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MetadataChip(
    item: MetadataItem,
    modifier: Modifier = Modifier,
    surfaceColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .squircleSurface(
                color = surfaceColor,
                cornerRadius = 50.dp,
                extension = SquircleExtension,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = accentColor,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = item.value,
            style = MiuixTheme.textStyles.footnote1,
            color = accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SongDetailCard(
    color: Color,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 18.dp,
    borderColor: Color? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onSurface) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .squircleSurface(
                    color = color,
                    cornerRadius = cornerRadius,
                    extension = SquircleExtension,
                )
                .then(
                    if (borderColor != null) {
                        Modifier.squircleBorder(
                            width = 0.5.dp,
                            color = borderColor,
                            cornerRadius = cornerRadius,
                            extension = SquircleExtension,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun SongDetailTextButton(
    text: String,
    onClick: () -> Unit,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: ImageVector? = null,
    textStyle: androidx.compose.ui.text.TextStyle = MiuixTheme.textStyles.button,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    contentColor: Color = MiuixTheme.colorScheme.onSurface,
) {
    val resolvedContentColor = if (selected) accentColor else contentColor
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .squircleSurface(
                color = surfaceColor,
                cornerRadius = 12.dp,
                extension = SquircleExtension,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = resolvedContentColor,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            style = textStyle,
            color = resolvedContentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SongDetailButton(
    onClick: () -> Unit,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onSurface) {
        Row(
            modifier = modifier
                .heightIn(min = 40.dp)
                .squircleSurface(
                    color = surfaceColor,
                    cornerRadius = 8.dp,
                    extension = SquircleExtension,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun SheetScoreCard(
    chart: SheetScoreUiState,
    surfaceColor: Color,
    actionSurfaceColor: Color,
    accentColor: Color,
    onRecord: () -> Unit,
    onDeleteRecord: (PlayRecordEntity) -> Unit,
) {
    var expanded by rememberSaveable(chart.sheet.sheetKey) { mutableStateOf(false) }
    var historyExpanded by rememberSaveable(chart.sheet.sheetKey) { mutableStateOf(false) }
    val expandInteractionSource = remember { MutableInteractionSource() }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "difficulty-chevron",
    )
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val accent = SongVisualUtils.difficultyColor(
        difficulty = chart.sheet.difficulty,
        darkTheme = darkTheme,
        brightenDark = true,
        fallbackColor = MiuixTheme.colorScheme.primary,
    )
    SongDetailCard(
        color = surfaceColor,
        borderColor = accent.copy(alpha = 0.58f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = expandInteractionSource,
                    indication = null,
                    onClick = { expanded = !expanded },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(50.dp)
                    .offset(x = (-16).dp)
                    .squircleSurface(
                        color = accent,
                        cornerRadius = 50.dp,
                        extension = SquircleExtension,
                    ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chart.sheet.difficulty.displayDifficulty(),
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    text = chart.sheet.noteDesigner.orEmpty(),
                    style = MiuixTheme.textStyles.body2,
                    color = accentColor.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = chart.sheet.internalLevelValue?.let(::formatPreciseLevel)
                    ?: chart.sheet.internalLevel
                    ?: chart.sheet.level,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = MiuixIcons.Demibold.ChevronForward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.58f),
                modifier = Modifier
                    .size(13.dp)
                    .rotate(chevronRotation),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(12.dp))
                BestScoreRow(chart.score, chart.sheet, accentColor)
                Spacer(Modifier.height(10.dp))
                NoteStatistics(chart.sheet)
                Spacer(Modifier.height(10.dp))
                SongDetailButton(
                    onClick = onRecord,
                    surfaceColor = actionSurfaceColor,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(MiuixIcons.Edit, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.score_record_action))
                }
                if (chart.history.isNotEmpty()) {
                    SongDetailTextButton(
                        text = stringResource(R.string.score_history),
                        onClick = { historyExpanded = !historyExpanded },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        surfaceColor = actionSurfaceColor,
                        accentColor = accentColor,
                        contentColor = accentColor,
                    )
                    AnimatedVisibility(visible = historyExpanded) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            chart.history.forEach { record -> HistoryRow(record, onDelete = { onDeleteRecord(record) }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteStatistics(sheet: SheetEntity) {
    val notes = listOf("TAP" to sheet.tap, "HOLD" to sheet.hold, "SLIDE" to sheet.slide, "TOUCH" to sheet.touch, "BREAK" to sheet.breakCount)
        .filter { it.second != null }
    if (notes.isEmpty()) return
    Text(
        text = stringResource(R.string.song_notes_title),
        style = MiuixTheme.textStyles.body2,
        fontWeight = FontWeight.Bold,
    )
    notes.forEach { (name, count) ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(count.toString(), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BestScoreRow(score: ScoreEntity?, sheet: SheetEntity, accentColor: Color) {
    if (score == null) {
        Text(
            text = stringResource(R.string.detail_no_scores),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        return
    }
    val maxDxScore = (sheet.total ?: 0) * 3
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.score_current_best),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Text(
                text = "${formatAchievement(score.achievement)}%  ${score.rank}",
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (score.dxScore > 0) {
                Text(
                    text = if (maxDxScore > 0) "${score.dxScore} / $maxDxScore" else score.dxScore.toString(),
                    style = MiuixTheme.textStyles.body2,
                )
            }
            val badges = listOfNotNull(
                ScoreRules.displayFc(score.fc),
                ScoreRules.displayFs(score.fs),
            ).joinToString("  ")
            if (badges.isNotEmpty()) {
                Text(
                    text = badges,
                    style = MiuixTheme.textStyles.footnote1,
                    color = accentColor,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(record: PlayRecordEntity, onDelete: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${formatAchievement(record.achievement)}%  ${record.rank}",
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = Instant.ofEpochMilli(record.playedAt)
                    .atZone(ZoneId.systemDefault())
                    .format(formatter),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        val details = buildList {
            if (record.dxScore > 0) add(record.dxScore.toString())
            ScoreRules.displayFc(record.fc)?.let(::add)
            ScoreRules.displayFs(record.fs)?.let(::add)
        }.joinToString("  ")
        if (details.isNotEmpty()) {
            Text(
                text = details,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = MiuixIcons.Delete,
                contentDescription = stringResource(R.string.score_delete_record),
                tint = MiuixTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ScoreEntryDialog(
    chart: SheetScoreUiState,
    saveStatus: ScoreSaveStatus,
    accentColor: Color,
    onInputChanged: () -> Unit,
    onSave: (ScoreInput) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentScore = chart.score
    var achievementText by rememberSaveable(chart.sheet.sheetKey) {
        mutableStateOf(currentScore?.achievement?.let(::formatAchievement).orEmpty())
    }
    var dxScoreText by rememberSaveable(chart.sheet.sheetKey) {
        mutableStateOf(currentScore?.dxScore?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var selectedFc by rememberSaveable(chart.sheet.sheetKey) { mutableStateOf(currentScore?.fc) }
    var selectedFs by rememberSaveable(chart.sheet.sheetKey) { mutableStateOf(currentScore?.fs) }
    val focusManager = LocalFocusManager.current
    val parsedAchievement = achievementText.trim().replace(',', '.').toDoubleOrNull()
    val parsedDxScore = if (dxScoreText.isBlank()) 0 else dxScoreText.trim().toIntOrNull()
    val maxDxScore = (chart.sheet.total ?: 0) * 3
    val input = if (parsedAchievement != null && parsedDxScore != null) {
        ScoreInput(parsedAchievement, parsedDxScore, selectedFc, selectedFs)
    } else {
        null
    }
    val validationError = input?.let { ScoreRules.validate(it, maxDxScore) }
    val inputIsValid = input != null && validationError == null
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val difficultyColor = SongVisualUtils.difficultyColor(
        difficulty = chart.sheet.difficulty,
        darkTheme = darkTheme,
        brightenDark = true,
        fallbackColor = MiuixTheme.colorScheme.primary,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 18.dp),
            cornerRadius = 8.dp,
            insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.score_entry_title),
                                style = MiuixTheme.textStyles.title2,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${chart.sheet.type.displayChartType()} · ${chart.sheet.difficulty.displayDifficulty()} · Lv.${chart.sheet.internalLevel ?: chart.sheet.level}",
                                style = MiuixTheme.textStyles.footnote1,
                                color = difficultyColor,
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = MiuixIcons.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                            )
                        }
                    }
                }
                item {
                    TextField(
                        value = achievementText,
                        onValueChange = {
                            achievementText = it
                            onInputChanged()
                        },
                        label = stringResource(R.string.score_achievement_hint),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = DpSize(width = 14.dp, height = 13.dp),
                        cornerRadius = 14.dp,
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.score_rank),
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = parsedAchievement
                                ?.takeIf { it.isFinite() && it in 0.0..101.0 }
                                ?.let(ScoreRules::calculateRank)
                                ?: stringResource(R.string.score_rank_pending),
                            style = MiuixTheme.textStyles.title3,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                    }
                }
                item {
                    TextField(
                        value = dxScoreText,
                        onValueChange = {
                            dxScoreText = it.filter(Char::isDigit)
                            onInputChanged()
                        },
                        label = if (maxDxScore > 0) {
                            stringResource(R.string.score_dx_hint_with_max, maxDxScore)
                        } else {
                            stringResource(R.string.score_dx_score)
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = DpSize(width = 14.dp, height = 13.dp),
                        cornerRadius = 14.dp,
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                    )
                }
                item {
                    ScoreOptionRow(
                        title = stringResource(R.string.score_combo),
                        options = listOf(null, "fc", "fcp", "ap", "app"),
                        selected = selectedFc,
                        accentColor = accentColor,
                        display = { ScoreRules.displayFc(it) ?: stringResource(R.string.common_none) },
                        onSelected = {
                            selectedFc = it
                            onInputChanged()
                        },
                    )
                }
                item {
                    ScoreOptionRow(
                        title = stringResource(R.string.score_sync),
                        options = listOf(null, "sync", "fs", "fsp", "fsd", "fsdp"),
                        selected = selectedFs,
                        accentColor = accentColor,
                        display = { ScoreRules.displayFs(it) ?: stringResource(R.string.common_none) },
                        onSelected = {
                            selectedFs = it
                            onInputChanged()
                        },
                    )
                }
                item {
                    ScoreEntryMessage(
                        achievementText = achievementText,
                        parsedAchievement = parsedAchievement,
                        dxScoreText = dxScoreText,
                        parsedDxScore = parsedDxScore,
                        validationError = validationError,
                        maxDxScore = maxDxScore,
                        saveStatus = saveStatus,
                    )
                }
                currentScore?.let { score ->
                    item {
                        Text(
                            text = stringResource(
                                R.string.score_current_best_value,
                                formatAchievement(score.achievement),
                                score.rank,
                            ),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
                item {
                    TextButton(
                        text = when (saveStatus) {
                            ScoreSaveStatus.Saving -> stringResource(R.string.score_saving)
                            ScoreSaveStatus.Saved -> stringResource(R.string.action_done)
                            else -> stringResource(R.string.score_save)
                        },
                        onClick = {
                            focusManager.clearFocus()
                            if (saveStatus == ScoreSaveStatus.Saved) onDismiss() else input?.let(onSave)
                        },
                        enabled = saveStatus == ScoreSaveStatus.Saved ||
                            (inputIsValid && saveStatus != ScoreSaveStatus.Saving),
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 8.dp,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreOptionRow(
    title: String,
    options: List<String?>,
    selected: String?,
    accentColor: Color,
    display: @Composable (String?) -> String,
    onSelected: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Text(
                    text = display(option),
                    style = MiuixTheme.textStyles.button,
                    color = if (isSelected) {
                        MiuixTheme.colorScheme.onPrimary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceContainerVariant
                    },
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .squircleSurface(
                            color = if (isSelected) {
                                accentColor
                            } else {
                                MiuixTheme.colorScheme.secondaryContainer
                            },
                            cornerRadius = 50.dp,
                            extension = SquircleExtension,
                        )
                        .clickable { onSelected(option) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ScoreEntryMessage(
    achievementText: String,
    parsedAchievement: Double?,
    dxScoreText: String,
    parsedDxScore: Int?,
    validationError: ScoreValidationError?,
    maxDxScore: Int,
    saveStatus: ScoreSaveStatus,
) {
    val message = when {
        achievementText.isNotBlank() && parsedAchievement == null ->
            stringResource(R.string.score_validation_achievement_format)
        validationError == ScoreValidationError.AchievementOutOfRange ->
            stringResource(R.string.score_validation_achievement_range)
        dxScoreText.isNotBlank() && parsedDxScore == null ->
            stringResource(R.string.score_validation_dx_format)
        validationError == ScoreValidationError.DxScoreOutOfRange && maxDxScore > 0 ->
            stringResource(R.string.score_validation_dx_range, maxDxScore)
        saveStatus == ScoreSaveStatus.Saved -> stringResource(R.string.score_saved)
        saveStatus == ScoreSaveStatus.Failed -> stringResource(R.string.score_save_failed)
        else -> null
    }
    if (message != null) {
        Text(
            text = message,
            style = MiuixTheme.textStyles.footnote1,
            color = when (saveStatus) {
                ScoreSaveStatus.Saved -> Color(0xFF2E7D32)
                else -> MiuixTheme.colorScheme.error
            },
        )
    }
}

@Composable
private fun DeleteRecordDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.widthIn(max = 420.dp),
            cornerRadius = 8.dp,
            insideMargin = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            Text(
                text = stringResource(R.string.score_delete_record_title),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.score_delete_record_message),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    cornerRadius = 8.dp,
                )
                TextButton(
                    text = stringResource(R.string.action_delete),
                    onClick = onConfirm,
                    cornerRadius = 8.dp,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun EmptySongState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.detail_catalog_required),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

private fun String.displayChartType(): String = when (lowercase()) {
    "std", "standard" -> "STD"
    else -> uppercase()
}

private fun String.displayDifficulty(): String = when (lowercase()) {
    "remaster" -> "RE: MASTER"
    else -> uppercase()
}

private fun formatAchievement(value: Double): String = value
    .toBigDecimal()
    .setScale(4, RoundingMode.HALF_UP)
    .toPlainString()

private fun formatPreciseLevel(value: Double): String {
    val normalized = value.toBigDecimal().stripTrailingZeros()
    return if (normalized.scale() < 1) normalized.setScale(1).toPlainString() else normalized.toPlainString()
}
