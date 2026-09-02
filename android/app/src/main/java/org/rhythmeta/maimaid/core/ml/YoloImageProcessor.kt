package org.rhythmeta.maimaid.core.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.core.graphics.createBitmap

internal data class LetterboxTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

internal object YoloImageProcessor {
    const val INPUT_SIZE = 640

    fun runDetector(
        bitmap: Bitmap,
        session: OrtSession,
        labels: List<String>,
        confidenceThreshold: Float = 0.25f,
    ): List<RecognizedRegion> {
        val (input, transform) = createLetterboxInput(bitmap)
        val environment = OrtEnvironment.getEnvironment()
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()),
        ).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                val output = result[0].value as Array<*>
                @Suppress("UNCHECKED_CAST")
                val channels = output[0] as Array<FloatArray>
                val detections = decodeDetections(channels, labels.size, confidenceThreshold)
                return NonMaximumSuppression.classAware(detections, 0.45f).mapNotNull { detection ->
                    val bounds = transform.toNormalizedSourceBounds(detection) ?: return@mapNotNull null
                    RecognizedRegion(
                        label = labels[detection.classIndex],
                        bounds = bounds,
                        confidence = detection.confidence,
                    )
                }
            }
        }
    }

    fun runClassifier(bitmap: Bitmap, session: OrtSession): FloatArray {
        val (input, _) = createLetterboxInput(bitmap)
        val environment = OrtEnvironment.getEnvironment()
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()),
        ).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                val output = result[0].value as Array<*>
                return (output[0] as FloatArray).copyOf()
            }
        }
    }

    private fun createLetterboxInput(bitmap: Bitmap): Pair<FloatArray, LetterboxTransform> {
        val scale = min(INPUT_SIZE.toFloat() / bitmap.width, INPUT_SIZE.toFloat() / bitmap.height)
        val scaledWidth = max(1, (bitmap.width * scale).roundToInt())
        val scaledHeight = max(1, (bitmap.height * scale).roundToInt())
        val offsetX = (INPUT_SIZE - scaledWidth) / 2f
        val offsetY = (INPUT_SIZE - scaledHeight) / 2f
        val target = createBitmap(INPUT_SIZE, INPUT_SIZE)
        Canvas(target).apply {
            drawColor(Color.rgb(114, 114, 114))
            drawBitmap(
                bitmap,
                null,
                RectF(offsetX, offsetY, offsetX + scaledWidth, offsetY + scaledHeight),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        }
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        target.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        target.recycle()
        val planeSize = INPUT_SIZE * INPUT_SIZE
        val input = FloatArray(planeSize * 3)
        pixels.forEachIndexed { index, pixel ->
            input[index] = Color.red(pixel) / 255f
            input[planeSize + index] = Color.green(pixel) / 255f
            input[planeSize * 2 + index] = Color.blue(pixel) / 255f
        }
        return input to LetterboxTransform(scale, offsetX, offsetY, bitmap.width, bitmap.height)
    }

    private fun decodeDetections(
        channels: Array<FloatArray>,
        classCount: Int,
        confidenceThreshold: Float,
    ): List<Detection> {
        if (channels.size < 4 + classCount) return emptyList()
        val result = ArrayList<Detection>()
        val candidateCount = channels[0].size
        for (candidate in 0 until candidateCount) {
            var bestClass = 0
            var bestConfidence = channels[4][candidate]
            for (classIndex in 1 until classCount) {
                val confidence = channels[4 + classIndex][candidate]
                if (confidence > bestConfidence) {
                    bestClass = classIndex
                    bestConfidence = confidence
                }
            }
            if (bestConfidence < confidenceThreshold) continue
            val centerX = channels[0][candidate]
            val centerY = channels[1][candidate]
            val width = channels[2][candidate]
            val height = channels[3][candidate]
            result += Detection(
                classIndex = bestClass,
                confidence = bestConfidence,
                left = centerX - width / 2f,
                top = centerY - height / 2f,
                right = centerX + width / 2f,
                bottom = centerY + height / 2f,
            )
        }
        return result
    }

    private fun LetterboxTransform.toNormalizedSourceBounds(detection: Detection): NormalizedRect? {
        val left = ((detection.left - offsetX) / scale).coerceIn(0f, sourceWidth.toFloat())
        val top = ((detection.top - offsetY) / scale).coerceIn(0f, sourceHeight.toFloat())
        val right = ((detection.right - offsetX) / scale).coerceIn(0f, sourceWidth.toFloat())
        val bottom = ((detection.bottom - offsetY) / scale).coerceIn(0f, sourceHeight.toFloat())
        if (right - left < 2f || bottom - top < 2f) return null
        return NormalizedRect(
            left = left / sourceWidth,
            top = top / sourceHeight,
            right = right / sourceWidth,
            bottom = bottom / sourceHeight,
        )
    }
}
