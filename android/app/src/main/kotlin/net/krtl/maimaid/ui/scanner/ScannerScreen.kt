package net.krtl.maimaid.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import net.krtl.maimaid.R
import net.krtl.maimaid.domain.model.Sheet
import net.krtl.maimaid.domain.usecase.RatingEngine
import net.krtl.maimaid.scanner.camera.CameraFrameBitmapConverter
import net.krtl.maimaid.scanner.model.ScannerImageType
import net.krtl.maimaid.scanner.model.ScannerMatch
import net.krtl.maimaid.scanner.model.ScannerRecognition
import net.krtl.maimaid.ui.app.AppContainer
import net.krtl.maimaid.ui.common.SongCover
import net.krtl.maimaid.ui.common.difficultyAccentColor
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.max

@Composable
fun ScannerScreen(
    container: AppContainer,
    innerPadding: PaddingValues,
    openSong: (String) -> Unit
) {
    val context = LocalContext.current
    val vm: ScannerViewModel = viewModel(
        factory = ScannerViewModelFactory(
            context = context,
            staticDataRepository = container.staticDataRepository,
            profileRepository = container.profileRepository,
            scoreRepository = container.scoreRepository,
            preferencesRepository = container.preferencesRepository
        )
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        decodeGalleryBitmap(context, uri)?.let(vm::analyzePhoto)
            ?: Toast.makeText(context, context.getString(R.string.scanner_error_load_photo), Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ScannerEvent.OpenSong -> openSong(event.songIdentifier)
                is ScannerEvent.Toast -> {
                    val message = if (event.detail.isNullOrBlank()) {
                        context.getString(event.messageRes)
                    } else {
                        context.getString(event.messageRes, event.detail)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(innerPadding)
    ) {
        if (hasCameraPermission) {
            ScannerCameraPreview(
                onBitmap = vm::onCameraBitmap,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CameraPermissionPrompt(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (state.showModelRegions && state.lastRecognition?.detections?.isNotEmpty() == true) {
            ScannerModelRegionOverlay(
                recognition = state.lastRecognition,
                modifier = Modifier.fillMaxSize()
            )
        }

        ScannerTopStatus(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        )

        OutlinedButton(
            onClick = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 70.dp, end = 16.dp)
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.scanner_pick_photo))
        }

        state.stableMatch?.let { match ->
            ScannerResultCard(
                match = match,
                saveInProgress = state.saveInProgress,
                onClick = {
                    vm.onResultTapped(match)
                },
                onReset = vm::reset,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            )
        }

        val reviewDraft = state.reviewDraft
        if (state.stableMatch == null && reviewDraft != null) {
            ReviewDraftCard(
                draft = reviewDraft,
                onReview = { vm.openReview() },
                onReset = vm::reset,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            )
        }
    }

    state.reviewDraft?.takeIf { state.isReviewOpen }?.let { draft ->
        ScoreConfirmationDialog(
            draft = draft,
            saveInProgress = state.saveInProgress,
            onDismiss = vm::closeReview,
            onSheetSelected = vm::selectReviewSheet,
            onSave = { rate, dxScore, fc, fs ->
                vm.saveReviewScore(rate, dxScore, fc, fs)
            }
        )
    }
}

@Composable
private fun ScannerCameraPreview(
    onBitmap: (android.graphics.Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val lastAnalyzedAt = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    LaunchedEffect(context, lifecycleOwner, previewView) {
        val cameraProvider = context.cameraProvider()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    try {
                        val now = System.currentTimeMillis()
                        if (now - lastAnalyzedAt.get() >= 450L && lastAnalyzedAt.compareAndSet(lastAnalyzedAt.get(), now)) {
                            CameraFrameBitmapConverter.toBitmap(imageProxy)?.let(onBitmap)
                        }
                    } finally {
                        imageProxy.close()
                    }
                }
            }
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageAnalysis
        )
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

@Composable
private fun ReviewDraftCard(
    draft: ScannerReviewDraft,
    onReview: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recognition = draft.recognition
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.scanner_fallback_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = buildList {
                    recognition.rate?.let { add(String.format(Locale.US, "%.4f%%", it)) }
                    recognition.difficulty?.let { add(displayDifficulty(it)) }
                    recognition.type?.let { add(it.uppercase(Locale.US)) }
                    if (draft.candidates.isNotEmpty()) add(draft.candidates.first().song.title)
                }.joinToString(" · ").ifBlank { stringResource(R.string.scanner_fallback_body) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.common_reset))
                }
                Button(onClick = onReview, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.scanner_result_review))
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionPrompt(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier.padding(24.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = stringResource(R.string.scanner_permission_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.scanner_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRequestPermission) {
                Text(stringResource(R.string.scanner_permission_grant))
            }
        }
    }
}

@Composable
private fun ScannerTopStatus(
    state: ScannerUiState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (state.stableMatch == null) Icons.Default.Search else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (state.stableMatch == null) MaterialTheme.colorScheme.primary else Color(0xFF2EAD5B)
            )
            Column {
                Text(
                    text = stringResource(state.statusMessageRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                state.statusDetail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerResultCard(
    match: ScannerMatch,
    saveInProgress: Boolean,
    onClick: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recognition = match.recognition
    val sheet = match.sheet
    val accent = sheet?.let { difficultyAccentColor(it.difficulty, it.type) } ?: MaterialTheme.colorScheme.primary
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SongCover(
                    imageName = match.song.imageName,
                    title = match.song.title,
                    modifier = Modifier.size(56.dp),
                    cornerRadius = 12
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = match.song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = match.song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (sheet != null) {
                        Text(
                            text = "${sheet.type.uppercase()} · ${displayDifficulty(sheet.difficulty)} · Lv.${sheet.internalLevel ?: sheet.level}",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                recognition.rate?.let { rate ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = String.format(Locale.US, "%.4f%%", rate),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = RatingEngine.calculateRank(rate),
                            style = MaterialTheme.typography.labelLarge,
                            color = accent,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recognition.dxScore?.let { AssistChip(onClick = {}, label = { Text(stringResource(R.string.scanner_result_dx_score, it)) }) }
                recognition.fc?.let { AssistChip(onClick = {}, label = { Text(displayFc(it)) }) }
                recognition.fs?.let { AssistChip(onClick = {}, label = { Text(displayFs(it)) }) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.common_reset))
                }
                Button(
                    onClick = onClick,
                    enabled = !saveInProgress,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (recognition.imageType == ScannerImageType.SCORE) {
                            stringResource(R.string.scanner_result_review)
                        } else {
                            stringResource(R.string.scanner_result_open_song)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreConfirmationDialog(
    draft: ScannerReviewDraft,
    saveInProgress: Boolean,
    onDismiss: () -> Unit,
    onSheetSelected: (String) -> Unit,
    onSave: (Double, Int, String?, String?) -> Unit
) {
    val selectedCandidate = draft.candidates.firstOrNull { it.sheet.sheetId == draft.selectedSheetId }
        ?: draft.candidates.firstOrNull()
        ?: return
    val sheet = selectedCandidate.sheet
    var rate by remember(draft.recognition, draft.selectedSheetId) {
        mutableStateOf(draft.recognition.rate?.let { String.format(Locale.US, "%.4f", it) }.orEmpty())
    }
    var dxScore by remember(draft.recognition, draft.selectedSheetId) {
        mutableStateOf(draft.recognition.dxScore?.toString().orEmpty())
    }
    var fc by remember(draft.recognition, draft.selectedSheetId) { mutableStateOf(draft.recognition.fc.orEmpty()) }
    var fs by remember(draft.recognition, draft.selectedSheetId) { mutableStateOf(draft.recognition.fs.orEmpty()) }
    val parsedRate = rate.toDoubleOrNull()
    val parsedDxScore = dxScore.toIntOrNull() ?: 0
    val maxDxScore = (sheet.total ?: 0) * 3
    val valid = parsedRate != null &&
        parsedRate in 0.0..101.0 &&
        parsedDxScore >= 0 &&
        (maxDxScore == 0 || parsedDxScore <= maxDxScore)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null) },
        title = { Text(stringResource(R.string.scanner_confirm_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.scanner_confirm_sheet),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                ScannerSheetPicker(
                    candidates = draft.candidates,
                    selectedSheetId = sheet.sheetId,
                    onSelected = onSheetSelected
                )
                Text(
                    text = "${selectedCandidate.song.title} · ${sheet.type.uppercase()} ${displayDifficulty(sheet.difficulty)} · Lv.${sheet.internalLevel ?: sheet.level}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text(stringResource(R.string.scanner_confirm_rate)) },
                    suffix = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dxScore,
                    onValueChange = { dxScore = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.scanner_confirm_dx_score)) },
                    suffix = if (maxDxScore > 0) {
                        { Text("/ $maxDxScore") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ScannerStatusPicker(
                    title = stringResource(R.string.scanner_confirm_combo),
                    options = listOf("", "fc", "fcp", "ap", "app"),
                    selected = fc,
                    display = ::displayFc,
                    onSelected = { fc = it }
                )
                ScannerStatusPicker(
                    title = stringResource(R.string.scanner_confirm_sync),
                    options = listOf("", "sync", "fs", "fsp", "fsd", "fsdp"),
                    selected = fs,
                    display = ::displayFs,
                    onSelected = { fs = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(parsedRate ?: 0.0, parsedDxScore, fc.ifBlank { null }, fs.ifBlank { null }) },
                enabled = valid && !saveInProgress
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerSheetPicker(
    candidates: List<ScannerReviewCandidate>,
    selectedSheetId: String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        candidates.forEach { candidate ->
            val sheet = candidate.sheet
            FilterChip(
                selected = sheet.sheetId == selectedSheetId,
                onClick = { onSelected(sheet.sheetId) },
                label = {
                    Text(
                        text = "${candidate.song.title} · ${sheet.type.uppercase()} ${displayDifficulty(sheet.difficulty)} Lv.${sheet.internalLevel ?: sheet.level}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerStatusPicker(
    title: String,
    options: List<String>,
    selected: String,
    display: (String) -> String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    label = {
                        Text(
                            if (option.isBlank()) {
                                stringResource(R.string.scanner_confirm_none)
                            } else {
                                display(option)
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ScannerModelRegionOverlay(
    recognition: ScannerRecognition?,
    modifier: Modifier = Modifier
) {
    val recognition = recognition ?: return
    val sourceWidth = recognition.sourceWidth.takeIf { it > 0 } ?: return
    val sourceHeight = recognition.sourceHeight.takeIf { it > 0 } ?: return
    val detections = recognition.detections
    Canvas(modifier = modifier) {
        val scale = max(
            size.width / sourceWidth.toFloat(),
            size.height / sourceHeight.toFloat()
        )
        val drawnWidth = sourceWidth * scale
        val drawnHeight = sourceHeight * scale
        val offsetX = (size.width - drawnWidth) / 2f
        val offsetY = (size.height - drawnHeight) / 2f
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 11.dp.toPx()
            isFakeBoldText = true
        }

        detections.forEach { box ->
            val left = offsetX + box.left * drawnWidth
            val top = offsetY + box.top * drawnHeight
            val width = (box.right - box.left) * drawnWidth
            val height = (box.bottom - box.top) * drawnHeight
            val labelPadding = 4.dp.toPx()
            val labelWidth = (textPaint.measureText(box.label) + labelPadding * 2).coerceAtLeast(24.dp.toPx())
            val labelHeight = 16.dp.toPx()
            val labelTop = (top - labelHeight).coerceAtLeast(0f)
            drawRect(
                color = Color(0xFF44FF66),
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 2.dp.toPx())
            )
            drawRect(
                color = Color(0xCC44FF66),
                topLeft = Offset(left, labelTop),
                size = Size(labelWidth, labelHeight),
                style = Fill
            )
            drawContext.canvas.nativeCanvas.drawText(
                box.label,
                left + labelPadding,
                labelTop + 11.5.dp.toPx(),
                textPaint
            )
        }
    }
}

private suspend fun Context.cameraProvider(): ProcessCameraProvider =
    ProcessCameraProvider.getInstance(this).await()

private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        { continuation.resume(get()) },
        Executor { command -> command.run() }
    )
    continuation.invokeOnCancellation { cancel(false) }
}

private fun decodeGalleryBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = false
    }.copy(Bitmap.Config.ARGB_8888, false)
}.getOrNull()

private fun displayDifficulty(difficulty: String): String = when (difficulty.lowercase()) {
    "remaster" -> "Re:MASTER"
    else -> difficulty.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}

private fun displayFc(value: String): String = when (value.lowercase()) {
    "fcp" -> "FC+"
    "app" -> "AP+"
    else -> value.uppercase()
}

private fun displayFs(value: String): String = when (value.lowercase()) {
    "sync" -> "S"
    "fsp" -> "FS+"
    "fsd" -> "FDX"
    "fsdp" -> "FDX+"
    else -> value.uppercase()
}
