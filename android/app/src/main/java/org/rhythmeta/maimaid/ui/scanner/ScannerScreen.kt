package org.rhythmeta.maimaid.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.ServerChartPolicy
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongAliasEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.core.ml.MaimaiScreenType
import org.rhythmeta.maimaid.core.ml.RecognizedRegion
import org.rhythmeta.maimaid.core.ml.ScannerMatch
import org.rhythmeta.maimaid.ui.catalog.SongJacket
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.song.ScoreEntrySheet
import org.rhythmeta.maimaid.ui.song.SheetScoreUiState
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ScannerScreen(
    container: AppContainer,
    songs: List<SongEntity>,
    sheets: List<SheetEntity>,
    aliases: List<SongAliasEntity>,
    scores: List<ScoreEntity>,
    server: String,
    showBoundingBoxes: Boolean,
    contentTopPadding: Dp,
    enabled: Boolean,
    onOpenSong: (String) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ScannerViewModel = viewModel(factory = ScannerViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cameraController = remember { ScannerCameraController() }
    val scope = rememberCoroutineScope()
    var modelPromptDismissed by remember { mutableStateOf(false) }
    var cameraPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraPermissionGranted = it
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                            val longest = max(info.size.width, info.size.height)
                            if (longest > MaximumPhotoDimension) {
                                val scale = MaximumPhotoDimension.toFloat() / longest
                                decoder.setTargetSize(
                                    (info.size.width * scale).toInt(),
                                    (info.size.height * scale).toInt(),
                                )
                            }
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    }.getOrNull()
                }
                if (bitmap != null) viewModel.analyzePhotoWhenReady(bitmap)
                else viewModel.showMessage(ScannerMessage.RecognitionFailed)
            }
        }
    }

    LaunchedEffect(songs, sheets, aliases, server) {
        viewModel.updateCatalog(songs, sheets, aliases, server)
    }
    LaunchedEffect(state.modelState) {
        if (state.modelState is ScannerModelState.DownloadRequired ||
            state.modelState is ScannerModelState.UpdateAvailable
        ) {
            modelPromptDismissed = false
        }
    }
    LaunchedEffect(enabled, cameraPermissionGranted) {
        if (enabled && !cameraPermissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(enabled) {
        onDispose { if (enabled) viewModel.reset() }
    }
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(MessageDurationMillis.milliseconds)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraPermissionGranted) {
            ScannerCameraPreview(
                enabled = enabled,
                controller = cameraController,
                onFrame = viewModel::analyzeLiveFrame,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ScannerPermissionState(
                topPadding = contentTopPadding,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            )
        }

        if (showBoundingBoxes && state.regions.isNotEmpty()) {
            ScannerDebugOverlay(
                regions = state.regions,
                imageWidth = state.imageWidth,
                imageHeight = state.imageHeight,
                modifier = Modifier.fillMaxSize(),
            )
        }

        IconButton(
            onClick = {
                photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp)
                .squircleSurface(Color.Black.copy(alpha = 0.32f), 22.dp, SquircleExtension),
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoLibrary,
                contentDescription = stringResource(R.string.scanner_library_button),
                tint = Color.White,
            )
        }

        ScannerStatusOverlay(
            state = state,
            modifier = Modifier.align(Alignment.Center),
        )

        ScannerModelGate(
            modelState = state.modelState,
            onDownload = viewModel::downloadModels,
            onRetry = viewModel::downloadModels,
            onCancel = viewModel::cancelModelDownload,
            modifier = Modifier.align(Alignment.Center),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 76.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val match = state.match
            AnimatedVisibility(
                visible = match?.recognition?.screenType == MaimaiScreenType.Score && match.sheet != null,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                IconButton(
                    onClick = {
                        val description = match?.let { captureDescription(it, server) }.orEmpty()
                        cameraController.capture(context, description) { saved ->
                            viewModel.showMessage(
                                if (saved) ScannerMessage.PhotoSaved else ScannerMessage.PhotoSaveFailed,
                            )
                        }
                    },
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .size(64.dp)
                        .squircleBorder(3.dp, Color.White, 32.dp, SquircleExtension),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .squircleSurface(Color.White, 26.dp, SquircleExtension),
                    )
                }
            }
            AnimatedVisibility(
                visible = match != null,
                enter = scaleIn(initialScale = 0.9f) + fadeIn(),
                exit = scaleOut(targetScale = 0.95f) + fadeOut(),
            ) {
                match?.let { scannerMatch ->
                    ScannerResultCard(
                        match = scannerMatch,
                        coverImageStore = container.coverImageStore,
                        server = server,
                        onClick = {
                            if (scannerMatch.recognition.screenType == MaimaiScreenType.Choose) {
                                viewModel.reset()
                                onOpenSong(scannerMatch.song.songIdentifier)
                            } else {
                                viewModel.openScoreEntry()
                            }
                        },
                    )
                }
            }
        }
    }

    state.match?.takeIf { it.sheet != null }?.let { match ->
        val sheet = requireNotNull(match.sheet)
        val maxDxScoreOverride = match.recognition.maxDxScore?.takeIf {
            it > 0 && sheet.type.equals("utage", ignoreCase = true)
        }
        ScoreEntrySheet(
            visible = state.scoreEntryVisible,
            song = match.song,
            chart = SheetScoreUiState(
                sheet = sheet,
                score = scores.firstOrNull { it.sheetKey == sheet.sheetKey },
                history = emptyList(),
                resolvedMetadata = ServerChartPolicy.metadata(sheet, server),
            ),
            saveStatus = state.scoreSaveStatus,
            initialAchievement = match.recognition.achievement,
            initialDxScore = match.recognition.dxScore,
            maxDxScoreOverride = maxDxScoreOverride,
            initialFc = match.recognition.comboStatus,
            initialFs = match.recognition.syncStatus,
            onInputChanged = viewModel::markScoreEntryChanged,
            onSave = { input -> viewModel.saveScore(input, maxDxScoreOverride) },
            onDismiss = viewModel::dismissScoreEntry,
        )
    }

    val promptState = state.modelState
    if (!modelPromptDismissed &&
        (promptState is ScannerModelState.DownloadRequired || promptState is ScannerModelState.UpdateAvailable)
    ) {
        val isUpdate = promptState is ScannerModelState.UpdateAvailable
        val totalBytes = when (promptState) {
            is ScannerModelState.DownloadRequired -> promptState.totalBytes
            is ScannerModelState.UpdateAvailable -> promptState.totalBytes
        }
        WindowDialog(
            show = true,
            title = stringResource(
                if (isUpdate) R.string.scanner_models_update_title
                else R.string.scanner_models_download_title,
            ),
            summary = stringResource(
                if (isUpdate) R.string.scanner_models_update_summary
                else R.string.scanner_models_download_summary,
                Formatter.formatShortFileSize(context, totalBytes),
            ),
            onDismissRequest = { modelPromptDismissed = true },
        ) {
            TextButton(
                text = stringResource(R.string.action_cancel),
                onClick = { modelPromptDismissed = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(10.dp))
            Button(
                onClick = {
                    modelPromptDismissed = true
                    viewModel.downloadModels()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.scanner_models_download_action))
            }
        }
    }
}

@Composable
private fun ScannerModelGate(
    modelState: ScannerModelState,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val blocksRecognition = when (modelState) {
        is ScannerModelState.Checking -> !modelState.cachedModelsAvailable
        is ScannerModelState.DownloadRequired -> true
        is ScannerModelState.Downloading -> !modelState.isUpdate
        is ScannerModelState.Failed -> !modelState.cachedModelsAvailable
        is ScannerModelState.Ready,
        is ScannerModelState.UpdateAvailable,
        -> false
    }
    val shouldShow = blocksRecognition ||
        modelState is ScannerModelState.Downloading ||
        modelState is ScannerModelState.Failed
    if (!shouldShow) return

    Column(
        modifier = modifier
            .padding(horizontal = 28.dp)
            .fillMaxWidth()
            .squircleSurface(Color.Black.copy(alpha = 0.72f), 16.dp, SquircleExtension)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (modelState) {
            is ScannerModelState.Checking -> {
                CircularProgressIndicator(size = 24.dp, strokeWidth = 2.dp)
                Text(stringResource(R.string.scanner_models_checking), color = Color.White)
            }
            is ScannerModelState.DownloadRequired -> {
                Text(stringResource(R.string.scanner_models_required), color = Color.White)
                Text(
                    stringResource(
                        R.string.scanner_models_download_size,
                        Formatter.formatShortFileSize(context, modelState.totalBytes),
                    ),
                    color = Color.White.copy(alpha = 0.72f),
                )
                Button(onClick = onDownload, colors = ButtonDefaults.buttonColorsPrimary()) {
                    Text(stringResource(R.string.scanner_models_download_action))
                }
            }
            is ScannerModelState.Downloading -> {
                Text(stringResource(R.string.scanner_models_downloading), color = Color.White)
                LinearProgressIndicator(
                    progress = modelState.progress.fraction,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = MiuixTheme.colorScheme.primary,
                        backgroundColor = Color.White.copy(alpha = 0.16f),
                    ),
                )
                Text(
                    stringResource(
                        R.string.catalog_sync_byte_progress,
                        Formatter.formatShortFileSize(context, modelState.progress.downloadedBytes),
                        Formatter.formatShortFileSize(context, modelState.progress.totalBytes),
                    ),
                    color = Color.White.copy(alpha = 0.72f),
                )
                TextButton(text = stringResource(R.string.action_cancel), onClick = onCancel)
            }
            is ScannerModelState.Failed -> {
                Text(stringResource(R.string.scanner_models_failed), color = Color.White)
                Text(
                    modelState.message,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColorsPrimary()) {
                    Text(stringResource(R.string.action_retry))
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ScannerPermissionState(topPadding: Dp, onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = topPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.CameraAlt,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(text = stringResource(R.string.scanner_camera_permission), color = Color.White)
        Spacer(Modifier.size(8.dp))
        IconButton(onClick = onRequestPermission) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.scanner_camera_permission_action),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun ScannerStatusOverlay(state: ScannerUiState, modifier: Modifier = Modifier) {
    val message = when {
        state.isProcessingPhoto -> stringResource(R.string.scanner_processing)
        state.message == ScannerMessage.LoadFailed -> stringResource(R.string.scanner_model_error)
        state.message == ScannerMessage.RecognitionFailed -> stringResource(R.string.scanner_error_title)
        state.message == ScannerMessage.PhotoSaved -> stringResource(R.string.scanner_photo_saved)
        state.message == ScannerMessage.PhotoSaveFailed -> stringResource(R.string.scanner_photo_error)
        state.message == ScannerMessage.OfflineModels -> stringResource(R.string.scanner_models_offline_cache)
        else -> null
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .squircleSurface(Color.Black.copy(alpha = 0.5f), 20.dp, SquircleExtension)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isProcessingPhoto) {
                CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
            }
            Text(text = message.orEmpty(), color = Color.White)
        }
    }
}

@Composable
private fun ScannerResultCard(
    match: ScannerMatch,
    coverImageStore: org.rhythmeta.maimaid.core.data.CoverImageStore,
    server: String,
    onClick: () -> Unit,
) {
    val recognition = match.recognition
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val difficulty = recognition.difficulty ?: match.sheet?.difficulty ?: "master"
    val chartType = recognition.chartType ?: match.sheet?.type ?: "dx"
    val difficultyColor = SongVisualUtils.difficultyColor(difficulty, chartType, darkTheme, brightenDark = true)
    val interactionSource = remember { MutableInteractionSource() }
    val rank = recognition.achievement?.let { org.rhythmeta.maimaid.core.data.ScoreRules.calculateRank(it) }
    val rankColor = rank?.let { org.rhythmeta.maimaid.ui.util.ScoreStatusColors.rank(it) }
    val isScore = recognition.screenType == MaimaiScreenType.Score
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .squircleSurface(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f), 16.dp, SquircleExtension)
            .squircleBorder(
                1.dp,
                if (isScore) difficultyColor.copy(alpha = 0.2f)
                else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                16.dp,
                SquircleExtension,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isScore) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 46.dp)
                    .squircleSurface(difficultyColor, 2.dp, SquircleExtension),
            )
            Spacer(Modifier.width(12.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (isScore) 0.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongJacket(match.song.imageName, coverImageStore, 40.dp, 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
            if (isScore) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = chartType.displayChartType(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MiuixTheme.textStyles.body2.copy(
                                fontSize = 8.sp,
                                lineHeight = 9.sp,
                            ),
                            modifier = Modifier
                                .squircleSurface(
                                    SongVisualUtils.chartTypeColor(chartType, darkTheme, difficultyColor),
                                    4.dp,
                                    SquircleExtension,
                                )
                                .padding(horizontal = 3.dp, vertical = 0.5.dp),
                        )
                        Text(
                            text = match.song.title,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            fontWeight = FontWeight.Bold,
                            style = MiuixTheme.textStyles.body2.copy(
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                            ),
                            modifier = Modifier.fillMaxWidth().basicMarquee(),
                        )
                    }
                    Text(
                        text = difficulty.displayDifficulty(),
                        color = difficultyColor,
                        fontWeight = FontWeight.Bold,
                        style = MiuixTheme.textStyles.body2.copy(
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                        ),
                    )
                } else {
                    Text(
                        text = match.song.title,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        fontWeight = FontWeight.Bold,
                        style = MiuixTheme.textStyles.body2.copy(
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                        ),
                        modifier = Modifier.fillMaxWidth().basicMarquee(),
                    )
                    Text(
                        text = match.song.artist,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        style = MiuixTheme.textStyles.body2.copy(
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                        ),
                        modifier = Modifier.basicMarquee(),
                    )
                }
            }
            if (isScore && recognition.achievement != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(
                        text = "${"%.4f".format(recognition.achievement)}%",
                        fontWeight = FontWeight.Bold,
                        style = MiuixTheme.textStyles.body2.copy(
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                        ),
                    )
                    rank?.let {
                        Text(
                            text = it,
                            color = rankColor ?: difficultyColor,
                            fontWeight = FontWeight.Black,
                            style = MiuixTheme.textStyles.body2.copy(
                                fontSize = 9.sp,
                                lineHeight = 10.sp,
                            ),
                        )
                    }
                }
            }
            if (isScore) match.sheet?.let { sheet ->
                Text(
                    text = ServerChartPolicy.metadata(sheet, server).displayLevel,
                    color = difficultyColor.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Black,
                    style = MiuixTheme.textStyles.body1.copy(
                        fontSize = 20.sp,
                        lineHeight = 22.sp,
                    ),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 4.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun ScannerDebugOverlay(
    regions: List<RecognizedRegion>,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (imageWidth <= 0 || imageHeight <= 0) return@Canvas
        val imageAspect = imageWidth.toFloat() / imageHeight
        val viewAspect = size.width / size.height
        val scale = if (imageAspect > viewAspect) size.height / imageHeight else size.width / imageWidth
        val renderedWidth = imageWidth * scale
        val renderedHeight = imageHeight * scale
        val offsetX = (size.width - renderedWidth) / 2f
        val offsetY = (size.height - renderedHeight) / 2f
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 11.sp.toPx()
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(220, 255, 214, 10)
        }
        regions.forEach { region ->
            val bounds = region.bounds
            val left = offsetX + bounds.left * renderedWidth
            val top = offsetY + bounds.top * renderedHeight
            val right = offsetX + bounds.right * renderedWidth
            val bottom = offsetY + bounds.bottom * renderedHeight
            drawRect(
                color = Color(0xFFFFD60A),
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
            val label = "${region.label} ${(region.confidence * 100).toInt()}%"
            val horizontalPadding = 5.dp.toPx()
            val verticalPadding = 3.dp.toPx()
            val labelWidth = labelPaint.measureText(label) + horizontalPadding * 2
            val labelHeight = labelPaint.fontMetrics.run { bottom - top } + verticalPadding * 2
            val labelLeft = left.coerceIn(0f, (size.width - labelWidth).coerceAtLeast(0f))
            val labelTop = (top - labelHeight).takeIf { it >= 0f }
                ?: bottom.coerceAtMost(size.height - labelHeight)
            drawContext.canvas.nativeCanvas.drawRoundRect(
                labelLeft,
                labelTop,
                labelLeft + labelWidth,
                labelTop + labelHeight,
                4.dp.toPx(),
                4.dp.toPx(),
                labelBackgroundPaint,
            )
            drawContext.canvas.nativeCanvas.drawText(
                label,
                labelLeft + horizontalPadding,
                labelTop + verticalPadding - labelPaint.fontMetrics.top,
                labelPaint,
            )
        }
    }
}

private fun captureDescription(match: ScannerMatch, server: String): String = listOfNotNull(
    match.song.title,
    match.sheet?.let { "LV${ServerChartPolicy.metadata(it, server).displayLevel}" },
    match.recognition.difficulty?.uppercase(),
    match.recognition.chartType?.uppercase(),
).joinToString(" ")

private fun String.displayChartType(): String = if (equals("std", true)) "STD" else uppercase()
private fun String.displayDifficulty(): String = if (equals("remaster", true)) "RE:MASTER" else uppercase()
private const val MaximumPhotoDimension = 2_560
private const val MessageDurationMillis = 2_500L
