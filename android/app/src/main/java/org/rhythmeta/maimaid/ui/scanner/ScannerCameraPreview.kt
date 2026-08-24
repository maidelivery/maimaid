package org.rhythmeta.maimaid.ui.scanner

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal class ScannerCameraController {
    var imageCapture: ImageCapture? = null

    fun capture(
        context: Context,
        description: String,
        onResult: (Boolean) -> Unit,
    ) {
        val capture = imageCapture ?: run {
            onResult(false)
            return
        }
        val name = "maimaid_${LegacyFileTimestamp.format(System.currentTimeMillis())}"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.TITLE, name)
            put(MediaStore.Images.Media.ARTIST, "maimaid")
            put(MediaStore.Images.Media.DESCRIPTION, description)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/maimaid")
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = onResult(true)
                override fun onError(exception: ImageCaptureException) = onResult(false)
            },
        )
    }

    private companion object {
        @Suppress("DEPRECATION")
        val LegacyFileTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT)
    }
}

@Composable
internal fun ScannerCameraPreview(
    enabled: Boolean,
    controller: ScannerCameraController,
    onFrame: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val frameCounter = remember { AtomicInteger(0) }
    val resolutionSelector = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()
    }

    AndroidView(factory = { previewView }, modifier = modifier)

    LaunchedEffect(enabled, lifecycleOwner, previewView) {
        val provider = context.cameraProvider()
        provider.unbindAll()
        controller.imageCapture = null
        if (!enabled) return@LaunchedEffect
        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { useCase ->
                useCase.setAnalyzer(analysisExecutor) { image ->
                    try {
                        if (frameCounter.incrementAndGet() % AnalyzeEveryNthFrame == 0) {
                            val bitmap = image.toUprightBitmap()
                            onFrame(bitmap)
                        }
                    } finally {
                        image.close()
                    }
                }
            }
        val capture = ImageCapture.Builder()
            .setResolutionSelector(resolutionSelector)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        controller.imageCapture = capture
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis,
            capture,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.imageCapture = null
            analysisExecutor.shutdown()
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
}

private suspend fun Context.cameraProvider(): ProcessCameraProvider =
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                runCatching(future::get).fold(
                    onSuccess = { continuation.resume(it) { _, _, _ -> } },
                    onFailure = { continuation.resumeWith(Result.failure(it)) },
                )
            },
            ContextCompat.getMainExecutor(this),
        )
    }

private fun ImageProxy.toUprightBitmap(): Bitmap {
    val source = toBitmap()
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return source
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also {
        source.recycle()
    }
}

private const val AnalyzeEveryNthFrame = 10
