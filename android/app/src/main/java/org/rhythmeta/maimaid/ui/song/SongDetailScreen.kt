package org.rhythmeta.maimaid.ui.song

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.ScoreInput
import org.rhythmeta.maimaid.core.data.RatingUtils
import org.rhythmeta.maimaid.core.data.ScoreRules
import org.rhythmeta.maimaid.core.data.ScoreToleranceCalculator
import org.rhythmeta.maimaid.core.data.ScoreValidationError
import org.rhythmeta.maimaid.core.data.CommunityAliasDailyQuota
import org.rhythmeta.maimaid.core.data.CommunityAliasMyCandidate
import org.rhythmeta.maimaid.core.data.StaticBundleResponse
import org.rhythmeta.maimaid.core.database.PlayRecordEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.ui.catalog.ScoreEntrySongCard
import org.rhythmeta.maimaid.ui.common.openExternalApp
import org.rhythmeta.maimaid.ui.components.ExpandableBottomSheet
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.components.appTextFieldColors
import org.rhythmeta.maimaid.ui.components.dashedSquircleBorder
import org.rhythmeta.maimaid.ui.community.CommunityAliasMessage
import org.rhythmeta.maimaid.ui.community.SongCommunityAliasUiState
import org.rhythmeta.maimaid.ui.community.SongCommunityAliasViewModel
import org.rhythmeta.maimaid.ui.components.squircleShape
import org.rhythmeta.maimaid.ui.util.ScoreStatusColors
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.window.WindowListPopup
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

private data class SongDisplayAlias(
    val text: String,
    val isCommunity: Boolean,
)

private fun buildDisplayAliases(
    aliases: List<String>,
    approvedCommunityAliases: List<String>,
    myCandidates: List<CommunityAliasMyCandidate>,
): List<SongDisplayAlias> {
    val approvedCommunityKeys = approvedCommunityAliases
        .mapTo(mutableSetOf(), ::normalizedAliasKey)
    val communityAliases = buildList {
        addAll(approvedCommunityAliases)
        addAll(
            myCandidates
                .filter { it.status in setOf("pool_private", "voting", "approved") }
                .map(CommunityAliasMyCandidate::aliasText),
        )
    }
    val seen = mutableSetOf<String>()
    return buildList {
        aliases.forEach { alias ->
            val key = normalizedAliasKey(alias)
            if (key.isNotEmpty() && seen.add(key)) {
                add(SongDisplayAlias(alias.trim(), key in approvedCommunityKeys))
            }
        }
        communityAliases.forEach { alias ->
            val key = normalizedAliasKey(alias)
            if (key.isNotEmpty() && seen.add(key)) {
                add(SongDisplayAlias(alias.trim(), isCommunity = true))
            } else if (key.isNotEmpty()) {
                val index = indexOfFirst { normalizedAliasKey(it.text) == key }
                if (index >= 0 && !get(index).isCommunity) {
                    set(index, get(index).copy(isCommunity = true))
                }
            }
        }
    }
}

private fun normalizedAliasKey(alias: String): String = alias.trim().lowercase()

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
    onOpenCommunityAliases: () -> Unit,
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
    val communityAliasViewModel = viewModel<SongCommunityAliasViewModel>(
        key = "community-alias-${song.songIdentifier}",
        factory = SongCommunityAliasViewModel.Factory(song.songIdentifier, container),
    )
    val communityAliasState by communityAliasViewModel.state.collectAsStateWithLifecycle()
    val displayAliases = remember(aliases, communityAliasState.approvedAliases, communityAliasState.candidates) {
        buildDisplayAliases(
            aliases = aliases,
            approvedCommunityAliases = communityAliasState.approvedAliases,
            myCandidates = communityAliasState.candidates,
        )
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val copiedConfirmation = stringResource(R.string.common_copied_to_clipboard)
    val showSnackbar: (String) -> Unit = { message ->
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Custom(ClipboardSnackbarDurationMillis),
            )
        }
    }
    val showCopiedSnackbar: () -> Unit = { showSnackbar(copiedConfirmation) }
    var recordToDelete by remember { mutableStateOf<PlayRecordEntity?>(null) }
    var retainedEntryChart by remember { mutableStateOf<SheetScoreUiState?>(null) }
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
                    aliases = displayAliases,
                    surfaceColor = surfaceColor,
                    accentColor = accentColor,
                    cachedCover = cachedCover,
                    metadataVersion = selectedChartVersion,
                    onJacketColor = { jacketColor = it },
                    onCopied = showCopiedSnackbar,
                    onMessage = showSnackbar,
                )
            }
            item {
                CommunityAliasSection(
                    state = communityAliasState,
                    surfaceColor = surfaceColor,
                    accentColor = accentColor,
                    showMessage = showSnackbar,
                    onOpenBoard = onOpenCommunityAliases,
                    onDraftChanged = communityAliasViewModel::setDraft,
                    onSubmit = communityAliasViewModel::submit,
                    onMessageConsumed = communityAliasViewModel::consumeMessage,
                )
            }
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
    LaunchedEffect(entryChart) {
        entryChart?.let { retainedEntryChart = it }
    }
    (entryChart ?: retainedEntryChart)?.let { presentedChart ->
        ScoreEntrySheet(
            visible = entryChart != null,
            song = song,
            chart = presentedChart,
            saveStatus = state.saveStatus,
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
    aliases: List<SongDisplayAlias>,
    surfaceColor: Color,
    accentColor: Color,
    cachedCover: File?,
    metadataVersion: String?,
    onJacketColor: (Color) -> Unit,
    onCopied: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val artist = song.artist.ifBlank { stringResource(R.string.song_artist_unknown) }
    val titleInteractionSource = remember { MutableInteractionSource() }
    val artistInteractionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    var artistLineStartPx by remember(song.songIdentifier) { mutableStateOf<Float?>(null) }
    val jacketShape = remember { squircleShape(26.dp) }
    var jacketMenuExpanded by remember { mutableStateOf(false) }
    var actionSourceFile by remember(cachedCover, song.imageName) { mutableStateOf(cachedCover) }
    var pendingLegacyDownload by remember { mutableStateOf<File?>(null) }
    val jacketSavedMessage = stringResource(R.string.song_jacket_saved)
    val jacketActionFailedMessage = stringResource(R.string.song_jacket_action_failed)
    val shareChooserTitle = stringResource(R.string.song_jacket_share_chooser)
    val legacyDownloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/*"),
    ) { uri ->
        val source = pendingLegacyDownload
        pendingLegacyDownload = null
        if (uri != null && source != null) {
            coroutineScope.launch {
                val saved = saveJacketToUri(context, source, uri)
                onMessage(if (saved) jacketSavedMessage else jacketActionFailedMessage)
            }
        }
    }
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
        Box {
            val openJacketMenu = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                jacketMenuExpanded = true
            }
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = jacketShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.18f),
                        spotColor = Color.Black.copy(alpha = 0.28f),
                    )
                    .squircleSurface(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                        cornerRadius = 26.dp,
                        extension = SquircleExtension,
                    )
                    .semantics {
                        onLongClick(label = context.getString(R.string.song_jacket_actions)) {
                            openJacketMenu()
                            true
                        }
                    }
                    .pointerInput(song.imageName) {
                        detectTapGestures(onLongPress = { openJacketMenu() })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
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
                        val bitmap = runCatching {
                            result.result.drawable.toBitmap(config = Bitmap.Config.ARGB_8888)
                        }.getOrNull()
                        bitmap?.let { loadedBitmap ->
                            SongVisualUtils.averageJacketColor(loadedBitmap)?.let(onJacketColor)
                            if (actionSourceFile == null) {
                                coroutineScope.launch {
                                    cacheJacketBitmap(context, loadedBitmap, song.imageName)?.let { cached ->
                                        actionSourceFile = cached
                                    }
                                }
                            }
                        }
                    },
                )
            }
            JacketActionMenu(
                expanded = jacketMenuExpanded,
                enabled = actionSourceFile != null,
                onDismiss = { jacketMenuExpanded = false },
                onDownload = {
                    jacketMenuExpanded = false
                    val source = actionSourceFile ?: return@JacketActionMenu
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        coroutineScope.launch {
                            val saved = saveJacketToDownloads(context, source, song.title)
                            onMessage(if (saved) jacketSavedMessage else jacketActionFailedMessage)
                        }
                    } else {
                        pendingLegacyDownload = source
                        legacyDownloadLauncher.launch(jacketDisplayName(song.title, source))
                    }
                },
                onCopy = {
                    jacketMenuExpanded = false
                    val source = actionSourceFile ?: return@JacketActionMenu
                    coroutineScope.launch {
                        val sharedJacket = prepareSharedJacket(context, source, song.title)
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        if (sharedJacket != null && clipboard != null) {
                            clipboard.setPrimaryClip(
                                ClipData.newUri(context.contentResolver, song.title, sharedJacket.uri),
                            )
                            onCopied()
                        } else {
                            onMessage(jacketActionFailedMessage)
                        }
                    }
                },
                onShare = {
                    jacketMenuExpanded = false
                    val source = actionSourceFile ?: return@JacketActionMenu
                    coroutineScope.launch {
                        val sharedJacket = prepareSharedJacket(context, source, song.title)
                        if (sharedJacket == null) {
                            onMessage(jacketActionFailedMessage)
                            return@launch
                        }
                        runCatching {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = sharedJacket.mimeType
                                putExtra(Intent.EXTRA_STREAM, sharedJacket.uri)
                                clipData = ClipData.newUri(
                                    context.contentResolver,
                                    song.title,
                                    sharedJacket.uri,
                                )
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, shareChooserTitle))
                        }.onFailure {
                            onMessage(jacketActionFailedMessage)
                        }
                    }
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
            onTextLayout = { result ->
                artistLineStartPx = if (result.lineCount > 0) {
                    result.getLineLeft(0).coerceAtLeast(0f)
                } else {
                    null
                }
            },
        )
        if (aliases.isNotEmpty()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                val aliasStartPadding = artistLineStartPx?.let { with(density) { it.toDp() } }
                    ?.coerceIn(0.dp, maxWidth) ?: 0.dp
                Row(
                    modifier = Modifier
                        .padding(start = aliasStartPadding)
                        .requiredWidth(maxWidth - aliasStartPadding + 16.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    aliases.forEach { alias ->
                        Text(
                            text = alias.text,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .squircleSurface(
                                    color = surfaceColor,
                                    cornerRadius = 50.dp,
                                    extension = SquircleExtension,
                                )
                                .then(
                                    if (alias.isCommunity) {
                                        Modifier.dashedSquircleBorder(
                                            width = 1.dp,
                                            color = accentColor.copy(alpha = 0.55f),
                                            cornerRadius = 50.dp,
                                        )
                                    } else {
                                        Modifier
                                    },
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
                    MetadataItem(it.toInt().toString(), Icons.Rounded.Timer, stringResource(R.string.song_bpm))
                },
                song.category.ifBlank { "maimai" }.let {
                    MetadataItem(it, Icons.Rounded.GridView, stringResource(R.string.song_category))
                },
                metadataVersion?.takeIf(String::isNotBlank)?.let {
                    MetadataItem(
                        SongVisualUtils.formatVersionName(it),
                        Icons.Rounded.Album,
                        stringResource(R.string.song_version),
                    )
                },
                song.releaseDate?.takeIf(String::isNotBlank)?.let {
                    MetadataItem(it, Icons.Rounded.CalendarMonth, stringResource(R.string.song_release_date))
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
private fun JacketActionMenu(
    expanded: Boolean,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    WindowListPopup(
        show = expanded,
        alignment = PopupPositionProvider.Align.End,
        enableWindowDim = false,
        onDismissRequest = onDismiss,
    ) {
        ListPopupColumn {
            JacketActionMenuItem(
                text = stringResource(R.string.song_jacket_download),
                icon = Icons.Rounded.Download,
                enabled = enabled,
                onClick = onDownload,
            )
            JacketActionMenuItem(
                text = stringResource(R.string.song_jacket_copy),
                icon = Icons.Rounded.ContentCopy,
                enabled = enabled,
                onClick = onCopy,
            )
            JacketActionMenuItem(
                text = stringResource(R.string.song_jacket_share),
                icon = Icons.Rounded.Share,
                enabled = enabled,
                onClick = onShare,
            )
        }
    }
}

@Composable
private fun JacketActionMenuItem(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (enabled) {
        MiuixTheme.colorScheme.onSurfaceContainer
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MiuixTheme.textStyles.body1,
            color = contentColor,
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
        val singleRow = values.size == 3 || maxWidth >= 520.dp
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
    onTextLayout: ((androidx.compose.ui.text.TextLayoutResult) -> Unit)? = null,
) {
    Text(
        text = text,
        style = style,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        onTextLayout = onTextLayout,
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .basicMarquee(),
    )
}

@Composable
private fun CommunityAliasSection(
    state: SongCommunityAliasUiState,
    surfaceColor: Color,
    accentColor: Color,
    showMessage: (String) -> Unit,
    onOpenBoard: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onMessageConsumed: () -> Unit,
) {
    val message = state.message
    val localizedMessage = message?.let {
        when (it) {
            CommunityAliasMessage.SubmitSuccess -> stringResource(R.string.community_alias_submit_success)
            CommunityAliasMessage.DuplicateLxns -> stringResource(R.string.community_alias_duplicate_lxns)
            CommunityAliasMessage.DuplicateCommunity -> stringResource(R.string.community_alias_duplicate_community)
            CommunityAliasMessage.AdminRejected -> stringResource(R.string.community_alias_admin_rejected)
            CommunityAliasMessage.Duplicate -> stringResource(R.string.community_alias_duplicate)
            CommunityAliasMessage.QuotaExceeded -> stringResource(R.string.community_alias_quota_exceeded)
            CommunityAliasMessage.LoginRequired -> stringResource(R.string.community_alias_login_required)
            CommunityAliasMessage.InvalidRequest -> stringResource(R.string.community_alias_invalid_request)
            CommunityAliasMessage.SubmitFailed -> state.errorMessage
                ?: stringResource(R.string.community_alias_submit_failed)
        }
    }
    LaunchedEffect(localizedMessage) {
        localizedMessage?.let(showMessage)
        if (localizedMessage != null) onMessageConsumed()
    }

    SongDetailCard(color = surfaceColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.song_aliases_title),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (state.approvedAliases.isEmpty()) {
                        stringResource(R.string.song_aliases_empty)
                    } else {
                        stringResource(R.string.song_aliases_count, state.approvedAliases.size)
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
                modifier = Modifier.clickable(onClick = onOpenBoard),
            )
        }
        Spacer(Modifier.height(12.dp))
        when {
            !state.isConfigured -> Text(
                text = stringResource(R.string.community_alias_unconfigured_message),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            !state.isAuthenticated -> Text(
                text = stringResource(R.string.community_alias_login_required),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            else -> {
                Text(
                    text = stringResource(
                        R.string.community_alias_daily_quota,
                        state.dailyUsedCount,
                        CommunityAliasDailyQuota,
                    ),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = state.dailyUsedCount.toFloat() / CommunityAliasDailyQuota,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = quotaColor(state.dailyUsedCount),
                        backgroundColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    ),
                    height = 6.dp,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextField(
                        value = state.draft,
                        onValueChange = onDraftChanged,
                        colors = appTextFieldColors(
                            accentColor = accentColor,
                            backgroundColor = accentColor
                                .copy(alpha = 0.14f)
                                .compositeOver(surfaceColor),
                        ),
                        label = stringResource(R.string.community_alias_submit_placeholder),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        cornerRadius = 12.dp,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    )
                    SongDetailButton(
                        onClick = onSubmit,
                        surfaceColor = accentColor.copy(alpha = 0.14f),
                        modifier = Modifier.height(52.dp),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.community_alias_submit_action),
                                tint = accentColor,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
        if (state.candidates.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.community_alias_my_submissions),
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(4.dp))
            state.candidates.take(4).forEach { candidate ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(candidate.aliasText, style = MiuixTheme.textStyles.footnote1)
                    Spacer(Modifier.weight(1f))
                    val status = communityAliasStatus(candidate.status)
                    Text(
                        text = stringResource(status.first),
                        style = MiuixTheme.textStyles.footnote2,
                        fontWeight = FontWeight.Bold,
                        color = status.second,
                    )
                }
            }
        }
    }
}

private fun quotaColor(used: Int): Color = when {
    used >= CommunityAliasDailyQuota -> Color(0xFFD65C5C)
    used >= CommunityAliasDailyQuota - 1 -> Color(0xFFE59A3A)
    else -> Color(0xFF36A65C)
}

private fun communityAliasStatus(status: String): Pair<Int, Color> = when (status) {
    "pool_private", "voting" -> R.string.community_alias_status_voting to Color(0xFF4385D8)
    "approved" -> R.string.community_alias_status_approved to Color(0xFF36A65C)
    "rejected" -> R.string.community_alias_status_rejected to Color(0xFFD65C5C)
    else -> R.string.community_alias_status_unknown to Color.Gray
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
                    imageVector = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = null,
                    tint = availabilityTextColor,
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
    val context = LocalContext.current
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
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.song_external_search),
                tint = accentColor,
                modifier = Modifier.size(20.dp),
            )
            SongDetailTextButton(
                text = stringResource(R.string.song_external_youtube),
                onClick = {
                    val opened = context.openExternalApp(
                        url = "https://www.youtube.com/results?search_query=maimai+$encodedTitle",
                        packageNames = listOf("com.google.android.youtube"),
                    )
                    if (!opened) {
                        Toast.makeText(context, R.string.external_app_unavailable, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
                surfaceColor = brandColors.youtubeSurface,
                icon = Icons.Rounded.PlayArrow,
                textStyle = MiuixTheme.textStyles.footnote1,
                contentColor = brandColors.youtubeContent,
            )
            SongDetailTextButton(
                text = stringResource(R.string.song_external_bilibili),
                onClick = {
                    val opened = context.openExternalApp(
                        url = "bilibili://search?keyword=maimai+$encodedTitle",
                        packageNames = listOf("tv.danmaku.bili", "tv.danmaku.bilibilihd"),
                    )
                    if (!opened) {
                        Toast.makeText(context, R.string.external_app_unavailable, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
                surfaceColor = brandColors.bilibiliSurface,
                icon = Icons.Rounded.MusicNote,
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
            style = MiuixTheme.textStyles.footnote2,
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
    val isUtage = chart.sheet.type.contains("utage", ignoreCase = true)
    val expandInteractionSource = remember { MutableInteractionSource() }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "difficulty-chevron",
    )
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val accent = SongVisualUtils.difficultyColor(
        difficulty = chart.sheet.difficulty,
        type = chart.sheet.type,
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
                if (!isUtage) {
                    Text(
                        text = chart.sheet.noteDesigner.orEmpty(),
                        style = MiuixTheme.textStyles.body2,
                        color = accentColor.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!expanded) {
                chart.score?.let { score ->
                    SheetScorePreview(score)
                    Spacer(Modifier.width(8.dp))
                }
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
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.58f),
                modifier = Modifier
                    .size(13.dp)
                    .rotate(chevronRotation),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Spacer(Modifier.height(12.dp))
                ChartFitStatsSection(chart.chartFit, accent)
                BestScoreRow(chart.score, chart.sheet, accentColor)

                NoteStatisticsSection(chart.sheet)

                if (!isUtage) {
                    (chart.sheet.internalLevelValue ?: chart.sheet.levelValue)
                        ?.takeIf { it > 0.0 }
                        ?.let { level -> RatingTableSection(level) }
                }

                if (chart.sheet.hasNoteData()) {
                    FaultToleranceCalculator(
                        sheet = chart.sheet,
                        accentColor = accent,
                    )
                }

                if (chart.history.isNotEmpty()) {
                    PlayHistorySection(
                        records = chart.history,
                        accentColor = accent,
                        onDeleteRecord = onDeleteRecord,
                    )
                }

                SongDetailButton(
                    onClick = onRecord,
                    surfaceColor = actionSurfaceColor,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.score_record_action))
                }
            }
        }
    }
}

@Composable
private fun ChartFitStatsSection(
    stat: StaticBundleResponse.ChartFitStat?,
    accentColor: Color,
) {
    if (stat == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .squircleSurface(
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                cornerRadius = 14.dp,
                extension = SquircleExtension,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChartFitStatItem(
            title = stringResource(R.string.song_chart_fit_difficulty),
            value = stat.fitDifficulty?.let(::formatFitDifficulty)
                ?: stringResource(R.string.song_chart_fit_not_available),
        )
        ChartFitStatDivider(accentColor)
        ChartFitStatItem(
            title = stringResource(R.string.song_chart_fit_average),
            value = stat.avg?.let(::formatAverageRate)
                ?: stringResource(R.string.song_chart_fit_not_available),
        )
        ChartFitStatDivider(accentColor)
        ChartFitStatItem(
            title = stringResource(R.string.song_chart_fit_samples),
            value = stat.cnt?.toInt()?.toString() ?: "0",
        )
    }
}

@Composable
private fun RowScope.ChartFitStatItem(title: String, value: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2.copy(fontSize = 15.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChartFitStatDivider(accentColor: Color) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(accentColor.copy(alpha = 0.10f)),
    )
}

@Composable
private fun SheetScorePreview(score: ScoreEntity) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = "${formatAchievement(score.achievement)}%",
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = score.rank,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Bold,
                color = ScoreStatusColors.rank(score.rank)
                    ?: MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            ScoreRules.displayFc(score.fc)?.let { combo ->
                ScorePreviewBadge(
                    text = combo,
                    color = ScoreStatusColors.combo(score.fc)
                        ?: MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
            displaySyncStatus(score.fs)?.let { sync ->
                ScorePreviewBadge(
                    text = sync,
                    color = ScoreStatusColors.sync(score.fs)
                        ?: MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
        }
    }
}

@Composable
private fun ScorePreviewBadge(text: String, color: Color) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 1,
        modifier = Modifier
            .squircleSurface(
                color = color,
                cornerRadius = 4.dp,
                extension = SquircleExtension,
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

private data class NoteBreakdownItem(
    val label: String,
    val count: Int,
    val weight: Double,
    val color: Color,
)

private fun SheetEntity.hasNoteData(): Boolean = listOf(tap, hold, slide, touch, breakCount)
    .any { (it ?: 0) > 0 }

@Composable
private fun CollapsibleDetailSection(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "$title-chevron",
    )
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = { onExpandedChange(!expanded) },
                )
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.58f),
                modifier = Modifier
                    .size(13.dp)
                    .rotate(chevronRotation),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Box(modifier = Modifier.padding(top = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun NoteStatisticsSection(sheet: SheetEntity) {
    if (!sheet.hasNoteData()) return
    var expanded by rememberSaveable(sheet.sheetKey) { mutableStateOf(false) }
    val notes = listOf(
        NoteBreakdownItem("TAP", sheet.tap ?: 0, 1.0, Color(0xFFFF2D78)),
        NoteBreakdownItem("HOLD", sheet.hold ?: 0, 2.0, Color(0xFFFF2D78)),
        NoteBreakdownItem("SLIDE", sheet.slide ?: 0, 3.0, Color(0xFF4D80FF)),
        NoteBreakdownItem("TOUCH", sheet.touch ?: 0, 1.0, Color(0xFF4D80FF)),
        NoteBreakdownItem("BREAK", sheet.breakCount ?: 0, 5.0, Color(0xFFFF9500)),
    ).filter { it.count > 0 }
    val totalWeight = notes.sumOf { it.count * it.weight }

    CollapsibleDetailSection(
        title = stringResource(R.string.song_notes_title),
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        Column {
            notes.forEachIndexed { index, note ->
                val fraction = if (totalWeight > 0.0) {
                    note.count * note.weight / totalWeight
                } else {
                    0.0
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (index % 2 == 0) {
                                MiuixTheme.colorScheme.onSurface.copy(alpha = 0.02f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = note.label,
                        style = MiuixTheme.textStyles.footnote2,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.width(44.dp),
                    )
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(
                                    maxOf(4.dp, maxWidth * fraction.toFloat())
                                        .coerceAtMost(maxWidth),
                                )
                                .background(note.color.copy(alpha = 0.5f)),
                        )
                    }
                    Text(
                        text = note.count.toString(),
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(40.dp),
                    )
                    Text(
                        text = "${(fraction * 100).toInt()}%",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(34.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BestScoreRow(score: ScoreEntity?, sheet: SheetEntity, accentColor: Color) {
    if (score == null) {
        Text(
            text = stringResource(R.string.detail_no_scores),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${formatAchievement(score.achievement)}%",
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = score.rank,
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                    color = ScoreStatusColors.rank(score.rank)
                        ?: MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (score.dxScore > 0) {
                Text(
                    text = if (maxDxScore > 0) "${score.dxScore} / $maxDxScore" else score.dxScore.toString(),
                    style = MiuixTheme.textStyles.body2,
                )
            }
            if (score.fc != null || score.fs != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScoreRules.displayFc(score.fc)?.let { combo ->
                        Text(
                            text = combo,
                            style = MiuixTheme.textStyles.footnote1,
                            color = ScoreStatusColors.combo(score.fc)
                                ?: MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        )
                    }
                    displaySyncStatus(score.fs)?.let { sync ->
                        Text(
                            text = sync,
                            style = MiuixTheme.textStyles.footnote1,
                            color = ScoreStatusColors.sync(score.fs) ?: accentColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingTableSection(level: Double) {
    var expanded by rememberSaveable(level) { mutableStateOf(false) }
    val rows = remember(level) {
        val values = RatingUtils.rankThresholds.asReversed().map { threshold ->
            Triple(
                threshold.rank,
                threshold.threshold,
                RatingUtils.calculate(level, threshold.threshold),
            )
        }
        values.mapIndexed { index, value ->
            RatingTableRow(
                rank = value.first,
                achievement = value.second,
                rating = value.third,
                delta = (value.third - (values.getOrNull(index + 1)?.third ?: 0)).coerceAtLeast(0),
            )
        }
    }
    CollapsibleDetailSection(
        title = stringResource(R.string.score_rating_table),
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.score_rating_achievement),
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.score_rating_score),
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(54.dp),
                )
                Text(
                    text = stringResource(R.string.score_rating_delta),
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(44.dp),
                )
            }
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (index % 2 == 0) {
                                MiuixTheme.colorScheme.onSurface.copy(alpha = 0.02f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.rank,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Bold,
                        color = ScoreStatusColors.rank(row.rank)
                            ?: MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.width(42.dp),
                    )
                    Text(
                        text = "${formatAchievement(row.achievement)}%",
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = row.rating.toString(),
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(54.dp),
                    )
                    Text(
                        text = row.delta.takeIf { it > 0 }?.let { "↑$it" }.orEmpty(),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(44.dp),
                    )
                }
            }
        }
    }
}

private data class RatingTableRow(
    val rank: String,
    val achievement: Double,
    val rating: Int,
    val delta: Int,
)

@Composable
private fun FaultToleranceCalculator(
    sheet: SheetEntity,
    accentColor: Color,
) {
    var targetAchievement by rememberSaveable(sheet.sheetKey) { mutableStateOf(100.5) }
    val tolerance = remember(sheet, targetAchievement) {
        ScoreToleranceCalculator.calculate(
            tapCount = sheet.tap ?: 0,
            holdCount = sheet.hold ?: 0,
            slideCount = sheet.slide ?: 0,
            touchCount = sheet.touch ?: 0,
            breakCount = sheet.breakCount ?: 0,
            targetAchievement = targetAchievement,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.score_tolerance_title),
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.score_tolerance_hint),
                style = MiuixTheme.textStyles.footnote2,
                color = accentColor,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RatingUtils.rankThresholds.asReversed().forEach { target ->
                val selected = target.threshold == targetAchievement
                val rankColor = ScoreStatusColors.rank(target.rank)
                    ?: MiuixTheme.colorScheme.onSurfaceVariantSummary
                Text(
                    text = target.rank,
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Bold,
                    color = rankColor,
                    modifier = Modifier
                        .squircleSurface(
                            color = if (selected) {
                                rankColor.copy(alpha = 0.16f)
                            } else {
                                MiuixTheme.colorScheme.surfaceContainerHigh
                            },
                            cornerRadius = 50.dp,
                            extension = SquircleExtension,
                        )
                        .clickable { targetAchievement = target.threshold }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToleranceResult(
                title = "GREAT",
                value = tolerance.great,
                color = Color(0xFFFF2D78),
                modifier = Modifier.weight(1f),
            )
            ToleranceResult(
                title = "GOOD",
                value = tolerance.good,
                color = Color(0xFF34C759),
                modifier = Modifier.weight(1f),
            )
            ToleranceResult(
                title = "MISS",
                value = tolerance.miss,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ToleranceResult(
    title: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .squircleSurface(
                color = color.copy(alpha = 0.08f),
                cornerRadius = 12.dp,
                extension = SquircleExtension,
            )
            .squircleBorder(
                width = 1.dp,
                color = color.copy(alpha = 0.15f),
                cornerRadius = 12.dp,
                extension = SquircleExtension,
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.Bold,
            color = color.copy(alpha = 0.8f),
        )
        Text(
            text = value.toString(),
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.score_tolerance_limit),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun PlayHistorySection(
    records: List<PlayRecordEntity>,
    accentColor: Color,
    onDeleteRecord: (PlayRecordEntity) -> Unit,
) {
    val sheetKey = records.first().sheetKey
    var expanded by rememberSaveable(sheetKey) { mutableStateOf(false) }
    var sortByDate by rememberSaveable(sheetKey) { mutableStateOf(true) }
    var page by rememberSaveable(sheetKey) { mutableStateOf(1) }
    val sortedRecords = remember(records, sortByDate) {
        if (sortByDate) {
            records.sortedByDescending(PlayRecordEntity::playedAt)
        } else {
            records.sortedWith(
                compareByDescending<PlayRecordEntity> { it.achievement }
                    .thenByDescending { it.playedAt },
            )
        }
    }
    val totalPages = ((sortedRecords.size + HistoryPageSize - 1) / HistoryPageSize).coerceAtLeast(1)
    val validPage = page.coerceIn(1, totalPages)
    val displayRecords = sortedRecords.drop((validPage - 1) * HistoryPageSize).take(HistoryPageSize)
    val bestRecordId = records.maxWithOrNull(
        compareBy<PlayRecordEntity> { it.achievement }.thenBy { it.playedAt },
    )?.id
    LaunchedEffect(totalPages) {
        page = page.coerceIn(1, totalPages)
    }

    CollapsibleDetailSection(
        title = stringResource(R.string.score_history),
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TabRowWithContour(
                    tabs = listOf(
                        stringResource(R.string.score_history_sort_time),
                        stringResource(R.string.score_history_sort_achievement),
                    ),
                    selectedTabIndex = if (sortByDate) 0 else 1,
                    onTabSelected = { index ->
                        sortByDate = index == 0
                        page = 1
                    },
                    modifier = Modifier.width(160.dp),
                    minWidth = 70.dp,
                    maxWidth = 80.dp,
                    height = 36.dp,
                    cornerRadius = 10.dp,
                )
            }
            displayRecords.forEachIndexed { index, record ->
                HistoryRow(
                    record = record,
                    isBest = record.id == bestRecordId,
                    alternate = index % 2 == 0,
                    accentColor = accentColor,
                    onDelete = { onDeleteRecord(record) },
                )
            }
            if (totalPages > 1) {
                HistoryPagination(
                    page = validPage,
                    totalPages = totalPages,
                    accentColor = accentColor,
                    onPageChange = { page = it },
                )
            }
        }
    }
}

@Composable
private fun HistoryPagination(
    page: Int,
    totalPages: Int,
    accentColor: Color,
    onPageChange: (Int) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onPageChange(page - 1) },
            enabled = page > 1,
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(R.string.score_history_previous_page),
                tint = if (page > 1) accentColor else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
            )
        }
        Box {
            Text(
                text = stringResource(R.string.score_history_page, page, totalPages),
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .squircleSurface(
                        color = MiuixTheme.colorScheme.surfaceContainerHigh,
                        cornerRadius = 50.dp,
                        extension = SquircleExtension,
                    )
                    .clickable(role = Role.Button) { menuExpanded = true }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            )
            WindowListPopup(
                show = menuExpanded,
                alignment = PopupPositionProvider.Align.End,
                enableWindowDim = false,
                onDismissRequest = { menuExpanded = false },
            ) {
                ListPopupColumn {
                    (1..totalPages).forEach { option ->
                        val selected = option == page
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onPageChange(option)
                                        menuExpanded = false
                                    },
                                )
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.score_history_page_option, option),
                                color = if (selected) accentColor else MiuixTheme.colorScheme.onSurfaceContainer,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        IconButton(
            onClick = { onPageChange(page + 1) },
            enabled = page < totalPages,
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.score_history_next_page),
                tint = if (page < totalPages) accentColor else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
            )
        }
    }
}

@Composable
private fun HistoryRow(
    record: PlayRecordEntity,
    isBest: Boolean,
    alternate: Boolean,
    accentColor: Color,
    onDelete: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    }
    val timeFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    }
    val playedAt = Instant.ofEpochMilli(record.playedAt).atZone(ZoneId.systemDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    isBest -> Modifier
                        .squircleSurface(
                            color = accentColor.copy(alpha = 0.1f),
                            cornerRadius = 8.dp,
                            extension = SquircleExtension,
                        )
                        .squircleBorder(
                            width = 1.5.dp,
                            color = accentColor,
                            cornerRadius = 8.dp,
                            extension = SquircleExtension,
                        )
                    alternate -> Modifier.background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.02f))
                    else -> Modifier
                },
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(72.dp)) {
            Text(
                text = playedAt.format(dateFormatter),
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = playedAt.format(timeFormatter),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = record.rank,
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Bold,
                    color = ScoreStatusColors.rank(record.rank)
                        ?: MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = "${formatAchievement(record.achievement)}%",
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (record.dxScore > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(10.dp),
                    )
                    Text(
                        text = record.dxScore.toString(),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScoreRules.displayFc(record.fc)?.let { combo ->
                Text(
                    text = combo,
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.Bold,
                    color = ScoreStatusColors.combo(record.fc)
                        ?: MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
            displaySyncStatus(record.fs)?.let { sync ->
                Text(
                    text = sync,
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.Bold,
                    color = ScoreStatusColors.sync(record.fs)
                        ?: MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.score_delete_record),
                    tint = MiuixTheme.colorScheme.error.copy(alpha = 0.65f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
internal fun ScoreEntrySheet(
    visible: Boolean,
    song: SongEntity,
    chart: SheetScoreUiState,
    saveStatus: ScoreSaveStatus,
    initialAchievement: Double? = null,
    initialDxScore: Int? = null,
    initialFc: String? = null,
    initialFs: String? = null,
    onInputChanged: () -> Unit,
    onSave: (ScoreInput) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentScore = chart.score
    var achievementText by rememberSaveable(chart.sheet.sheetKey) {
        mutableStateOf((initialAchievement ?: currentScore?.achievement)?.let(::formatAchievement).orEmpty())
    }
    var dxScoreText by rememberSaveable(chart.sheet.sheetKey) {
        mutableStateOf((initialDxScore ?: currentScore?.dxScore)?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var selectedFc by rememberSaveable(chart.sheet.sheetKey) { mutableStateOf(initialFc ?: currentScore?.fc) }
    var selectedFs by rememberSaveable(chart.sheet.sheetKey) { mutableStateOf(initialFs ?: currentScore?.fs) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(visible, chart.sheet.sheetKey) {
        if (visible) {
            achievementText = (initialAchievement ?: currentScore?.achievement)?.let(::formatAchievement).orEmpty()
            dxScoreText = (initialDxScore ?: currentScore?.dxScore)?.takeIf { it > 0 }?.toString().orEmpty()
            selectedFc = initialFc ?: currentScore?.fc
            selectedFs = initialFs ?: currentScore?.fs
        }
    }

    val parsedAchievement = achievementText.trim().toDoubleOrNull()
    val calculatedRank = parsedAchievement
        ?.takeIf { it.isFinite() && it in 0.0..101.0 }
        ?.let(ScoreRules::calculateRank)
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
        type = chart.sheet.type,
        darkTheme = darkTheme,
        brightenDark = true,
        fallbackColor = MiuixTheme.colorScheme.primary,
    )
    val canSubmit = inputIsValid && saveStatus != ScoreSaveStatus.Saving
    val headerActionVisible = canSubmit || saveStatus == ScoreSaveStatus.Saved
    val submitOrDismiss: () -> Unit = {
        focusManager.clearFocus()
        if (saveStatus == ScoreSaveStatus.Saved) onDismiss() else input?.let(onSave)
    }

    ExpandableBottomSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        expandActionLabel = stringResource(R.string.score_entry_expand),
        collapseActionLabel = stringResource(R.string.score_entry_collapse),
        expandedStateDescription = stringResource(R.string.score_entry_sheet_expanded),
        halfExpandedStateDescription = stringResource(R.string.score_entry_sheet_half),
        header = {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                )
            }
            Text(
                text = stringResource(R.string.score_entry_title),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
                maxLines = 1,
            )
            if (headerActionVisible) {
                IconButton(
                    onClick = submitOrDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(
                            if (saveStatus == ScoreSaveStatus.Saved) R.string.action_done else R.string.score_save,
                        ),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        },
    ) { topInset ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = topInset + 12.dp,
                end = 16.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ScoreEntrySongCard(
                    song = song,
                    sheet = chart.sheet,
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(16.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                        contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
                    ),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = calculatedRank ?: stringResource(R.string.score_rank_pending),
                            style = MiuixTheme.textStyles.title1,
                            fontWeight = FontWeight.Bold,
                            color = ScoreStatusColors.rank(calculatedRank)
                                ?: MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextField(
                            value = achievementText,
                            onValueChange = { value ->
                                acceptedAchievementInput(value)?.let { accepted ->
                                    if (accepted != achievementText) {
                                        achievementText = accepted
                                        onInputChanged()
                                    }
                                }
                            },
                            colors = appTextFieldColors(difficultyColor),
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
                        TextField(
                            value = dxScoreText,
                            onValueChange = { value ->
                                acceptedDxScoreInput(value, maxDxScore)?.let { accepted ->
                                    if (accepted != dxScoreText) {
                                        dxScoreText = accepted
                                        onInputChanged()
                                    }
                                }
                            },
                            colors = appTextFieldColors(difficultyColor),
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
                        ScoreDropdownField(
                            title = stringResource(R.string.score_combo),
                            options = listOf(null, "fc", "fcp", "ap", "app"),
                            selected = selectedFc,
                            display = { ScoreRules.displayFc(it) ?: stringResource(R.string.common_none) },
                            optionColor = ScoreStatusColors::combo,
                            onSelected = {
                                selectedFc = it
                                onInputChanged()
                            },
                        )
                        ScoreDropdownField(
                            title = stringResource(R.string.score_sync),
                            options = listOf(null, "sync", "fs", "fsp", "fsd", "fsdp"),
                            selected = selectedFs,
                            display = {
                                when (it) {
                                    "sync" -> "Sync"
                                    else -> ScoreRules.displayFs(it) ?: stringResource(R.string.common_none)
                                }
                            },
                            optionColor = ScoreStatusColors::sync,
                            onSelected = {
                                selectedFs = it
                                onInputChanged()
                            },
                        )
                    }
                }
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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        insideMargin = PaddingValues(14.dp),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                        ),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = stringResource(R.string.score_current_best),
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${formatAchievement(score.achievement)}%",
                                    style = MiuixTheme.textStyles.body2,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = score.rank,
                                    style = MiuixTheme.textStyles.body2,
                                    fontWeight = FontWeight.Bold,
                                    color = ScoreStatusColors.rank(score.rank)
                                        ?: MiuixTheme.colorScheme.onSurfaceContainerVariant,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = submitOrDismiss,
                    enabled = saveStatus == ScoreSaveStatus.Saved || canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Icon(
                        imageVector = if (saveStatus == ScoreSaveStatus.Saved) {
                            Icons.Rounded.Check
                        } else {
                            Icons.Rounded.Save
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when (saveStatus) {
                            ScoreSaveStatus.Saving -> stringResource(R.string.score_saving)
                            ScoreSaveStatus.Saved -> stringResource(R.string.action_done)
                            else -> stringResource(R.string.score_save)
                        },
                        style = MiuixTheme.textStyles.button,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreDropdownField(
    title: String,
    options: List<String?>,
    selected: String?,
    display: @Composable (String?) -> String,
    optionColor: (String?) -> Color? = { null },
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedColor = optionColor(selected)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .squircleSurface(
                        color = MiuixTheme.colorScheme.surfaceContainerHigh,
                        cornerRadius = 14.dp,
                        extension = SquircleExtension,
                    )
                    .clickable(role = Role.Button) { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = display(selected),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = selectedColor ?: MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = selectedColor ?: MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            WindowListPopup(
                show = expanded,
                alignment = PopupPositionProvider.Align.End,
                enableWindowDim = false,
                onDismissRequest = { expanded = false },
            ) {
                ListPopupColumn {
                    options.forEach { option ->
                        val isSelected = option == selected
                        val color = optionColor(option)
                            ?: MiuixTheme.colorScheme.onSurfaceContainer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) {
                                        color.copy(alpha = 0.08f)
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelected(option)
                                        expanded = false
                                    },
                                )
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = display(option),
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Medium,
                                color = color,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun acceptedAchievementInput(value: String): String? {
    val normalized = value.replace(',', '.')
    if (!AchievementInputPattern.matches(normalized)) return null
    val parsed = normalized.toDoubleOrNull()
    if (parsed != null && parsed > MaximumAchievement) return null
    return normalized
}

private fun acceptedDxScoreInput(value: String, maximum: Int): String? {
    if (value.isEmpty()) return value
    if (value.any { it !in '0'..'9' }) return null
    val parsed = value.toIntOrNull() ?: return null
    if (maximum > 0 && parsed > maximum) return null
    return value
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

private fun displaySyncStatus(value: String?): String? = ScoreRules.displayFs(value)?.let { display ->
    if (display == "S") "SYNC" else display
}

private fun formatAchievement(value: Double): String = value
    .toBigDecimal()
    .setScale(4, RoundingMode.HALF_UP)
    .toPlainString()

private fun formatAverageRate(value: Double): String = "${formatAchievement(value)}%"

private fun formatFitDifficulty(value: Double): String = value
    .toBigDecimal()
    .setScale(2, RoundingMode.HALF_UP)
    .toPlainString()

private fun formatPreciseLevel(value: Double): String {
    val normalized = value.toBigDecimal().stripTrailingZeros()
    return if (normalized.scale() < 1) normalized.setScale(1).toPlainString() else normalized.toPlainString()
}

private val AchievementInputPattern = Regex("^[0-9]{0,3}(?:\\.[0-9]{0,4})?$")
private const val MaximumAchievement = 101.0
private const val HistoryPageSize = 5
