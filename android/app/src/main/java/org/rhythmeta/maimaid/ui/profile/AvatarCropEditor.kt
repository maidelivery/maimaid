package org.rhythmeta.maimaid.ui.profile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.ui.components.squircleShape
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AvatarCropEditor(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onApply: (Bitmap) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background),
            topBar = {
                SmallTopAppBar(
                    title = stringResource(R.string.avatar_editor_title),
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                            )
                        }
                    },
                    defaultWindowInsetsPadding = true,
                )
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { paddingValues ->
            AvatarCropEditorContent(
                bitmap = bitmap,
                onApply = onApply,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun AvatarCropEditorContent(
    bitmap: Bitmap,
    onApply: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var rotation by remember(bitmap) { mutableFloatStateOf(0f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var cropCanvasSize by remember { mutableStateOf(IntSize.Zero) }
    val coveragePadding = with(density) { CoveragePadding.toPx() }
    val transformState = rememberTransformableState { _, zoomChange, panChange, rotationChange ->
        val guideDiameter = cropCanvasSize.width - with(density) { GuideInset.toPx() * 2f }
        if (guideDiameter <= 0f) return@rememberTransformableState
        val baseSize = baseDisplaySize(bitmap, guideDiameter)
        val nextScale = normalizedScale(scale * zoomChange, baseSize, guideDiameter, coveragePadding)
        scale = nextScale
        rotation += rotationChange
        offset = clampedOffset(
            proposed = offset + panChange,
            baseSize = baseSize,
            guideDiameter = guideDiameter,
            scale = nextScale,
            rotationDegrees = rotation,
            coveragePadding = coveragePadding,
        )
    }
    LaunchedEffect(bitmap, cropCanvasSize) {
        val guideDiameter = cropCanvasSize.width - with(density) { GuideInset.toPx() * 2f }
        if (guideDiameter > 0f) {
            scale = normalizedScale(
                proposed = 1f,
                baseSize = baseDisplaySize(bitmap, guideDiameter),
                guideDiameter = guideDiameter,
                coveragePadding = coveragePadding,
            )
            rotation = 0f
            offset = Offset.Zero
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cropSize = minOf(maxWidth - 32.dp, maxHeight * 0.55f)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.avatar_editor_hint),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .size(cropSize)
                    .onSizeChanged { cropCanvasSize = it }
                    .clip(squircleShape(28.dp))
                    .background(Color.Black)
                    .transformable(transformState)
                    .then(
                        Modifier.graphicsLayer {
                            clip = true
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val guideDiameter = size.minDimension - GuideInset.toPx() * 2f
                    drawIntoCanvas { composeCanvas ->
                        drawAvatarBitmap(
                            canvas = composeCanvas.nativeCanvas,
                            bitmap = bitmap,
                            baseSize = baseDisplaySize(bitmap, guideDiameter),
                            center = center,
                            scale = scale,
                            offset = offset,
                            rotationDegrees = rotation,
                        )
                    }
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = guideDiameter / 2f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            TextButton(
                text = stringResource(R.string.avatar_editor_reset),
                onClick = {
                    scope.launch {
                        val guideDiameter = cropCanvasSize.width - with(density) { GuideInset.toPx() * 2f }
                        if (guideDiameter <= 0f) return@launch
                        val targetScale = normalizedScale(
                            proposed = 1f,
                            baseSize = baseDisplaySize(bitmap, guideDiameter),
                            guideDiameter = guideDiameter,
                            coveragePadding = coveragePadding,
                        )
                        val startScale = scale
                        val startRotation = rotation
                        val startOffset = offset
                        animate(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 280,
                                easing = FastOutSlowInEasing,
                            ),
                        ) { progress, _ ->
                            scale = startScale + (targetScale - startScale) * progress
                            rotation = startRotation * (1f - progress)
                            offset = startOffset * (1f - progress)
                        }
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val guideDiameter = cropCanvasSize.width - with(density) { GuideInset.toPx() * 2f }
                    if (guideDiameter > 0f) {
                        onApply(
                            renderCroppedAvatar(
                                original = bitmap,
                                previewBaseSize = baseDisplaySize(bitmap, guideDiameter),
                                scale = scale,
                                offset = offset,
                                rotationDegrees = rotation,
                                guideDiameter = guideDiameter,
                            ),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                colors = ButtonDefaults.buttonColorsPrimary(),
                insideMargin = PaddingValues(vertical = 14.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.avatar_editor_apply))
            }
        }
    }
}

private fun baseDisplaySize(bitmap: Bitmap, cropTargetSize: Float): Offset {
    val aspectRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
    return if (aspectRatio >= 1f) {
        Offset(cropTargetSize * aspectRatio, cropTargetSize)
    } else {
        Offset(cropTargetSize, cropTargetSize / aspectRatio)
    }
}

private fun normalizedScale(
    proposed: Float,
    baseSize: Offset,
    guideDiameter: Float,
    coveragePadding: Float,
): Float {
    val minimumScale = ((guideDiameter + coveragePadding * 2f) / min(baseSize.x, baseSize.y))
        .coerceIn(1f, MaximumScale)
    return proposed.coerceIn(minimumScale, MaximumScale)
}

private fun clampedOffset(
    proposed: Offset,
    baseSize: Offset,
    guideDiameter: Float,
    scale: Float,
    rotationDegrees: Float,
    coveragePadding: Float,
): Offset {
    val radians = Math.toRadians(rotationDegrees.toDouble()).toFloat()
    val cosine = cos(radians)
    val sine = sin(radians)
    val localX = proposed.x * cosine + proposed.y * sine
    val localY = -proposed.x * sine + proposed.y * cosine
    val guideRadius = guideDiameter / 2f + coveragePadding
    val horizontalLimit = max(baseSize.x * scale / 2f - guideRadius, 0f)
    val verticalLimit = max(baseSize.y * scale / 2f - guideRadius, 0f)
    val clampedX = localX.coerceIn(-horizontalLimit, horizontalLimit)
    val clampedY = localY.coerceIn(-verticalLimit, verticalLimit)
    return Offset(
        x = clampedX * cosine - clampedY * sine,
        y = clampedX * sine + clampedY * cosine,
    )
}

private fun renderCroppedAvatar(
    original: Bitmap,
    previewBaseSize: Offset,
    scale: Float,
    offset: Offset,
    rotationDegrees: Float,
    guideDiameter: Float,
): Bitmap {
    val output = Bitmap.createBitmap(AvatarExportSize, AvatarExportSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val exportSize = AvatarExportSize.toFloat()
    val exportRatio = exportSize / guideDiameter
    val exportBaseSize = previewBaseSize * exportRatio
    drawAvatarBitmap(
        canvas = canvas,
        bitmap = original,
        baseSize = exportBaseSize,
        center = Offset(exportSize / 2f, exportSize / 2f),
        scale = scale,
        offset = offset * exportRatio,
        rotationDegrees = rotationDegrees,
    )
    return output
}

private fun drawAvatarBitmap(
    canvas: Canvas,
    bitmap: Bitmap,
    baseSize: Offset,
    center: Offset,
    scale: Float,
    offset: Offset,
    rotationDegrees: Float,
) {
    canvas.save()
    canvas.translate(center.x + offset.x, center.y + offset.y)
    canvas.rotate(rotationDegrees)
    canvas.scale(scale, scale)
    canvas.drawBitmap(
        bitmap,
        null,
        RectF(
            -baseSize.x / 2f,
            -baseSize.y / 2f,
            baseSize.x / 2f,
            baseSize.y / 2f,
        ),
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
    )
    canvas.restore()
}

private val GuideInset = 12.dp
private val CoveragePadding = 8.dp
private const val MaximumScale = 4f
private const val AvatarExportSize = 1024
