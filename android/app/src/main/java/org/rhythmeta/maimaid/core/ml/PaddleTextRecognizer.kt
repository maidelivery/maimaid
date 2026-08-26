package org.rhythmeta.maimaid.core.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import kotlinx.serialization.json.Json
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal data class OcrText(
    val text: String,
    val confidence: Float,
)

internal class PaddleTextRecognizer(
    private val session: OrtSession,
    characterFile: File,
) {
    private val characters = loadPaddleCharacters(characterFile)

    fun recognize(
        bitmap: Bitmap,
        bounds: NormalizedRect,
    ): OcrText? {
        val crop = bitmap.crop(bounds) ?: return null
        return try {
            recognizeCrop(crop)
        } finally {
            crop.recycle()
        }
    }

    private fun recognizeCrop(bitmap: Bitmap): OcrText? {
        val targetWidth = min(
            ModelWidth,
            max(MinimumWidth, ceil(ModelHeight * bitmap.width.toFloat() / bitmap.height).toInt()),
        )
        val resizedWidth = min(
            targetWidth,
            max(1, (ModelHeight * bitmap.width.toFloat() / bitmap.height).toInt()),
        )
        val resized = Bitmap.createBitmap(targetWidth, ModelHeight, Bitmap.Config.ARGB_8888)
        Canvas(resized).apply {
            drawColor(Color.BLACK)
            drawBitmap(
                bitmap,
                null,
                RectF(0f, 0f, resizedWidth.toFloat(), ModelHeight.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        }
        val pixels = IntArray(targetWidth * ModelHeight)
        resized.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, ModelHeight)
        resized.recycle()
        val planeSize = targetWidth * ModelHeight
        val input = FloatArray(planeSize * 3)
        pixels.forEachIndexed { index, pixel ->
            input[index] = Color.blue(pixel) / 127.5f - 1f
            input[planeSize + index] = Color.green(pixel) / 127.5f - 1f
            input[planeSize * 2 + index] = Color.red(pixel) / 127.5f - 1f
        }
        val environment = OrtEnvironment.getEnvironment()
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, ModelHeight.toLong(), targetWidth.toLong()),
        ).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                val batch = result[0].value as Array<*>
                @Suppress("UNCHECKED_CAST")
                val timesteps = batch[0] as Array<FloatArray>
                return decodeCtc(timesteps)
            }
        }
    }

    private fun decodeCtc(timesteps: Array<FloatArray>): OcrText? {
        val text = StringBuilder()
        var previousIndex = BlankIndex
        var confidenceSum = 0f
        var characterCount = 0
        timesteps.forEach { probabilities ->
            var bestIndex = 0
            var bestProbability = probabilities[0]
            for (index in 1 until probabilities.size) {
                if (probabilities[index] > bestProbability) {
                    bestIndex = index
                    bestProbability = probabilities[index]
                }
            }
            if (bestIndex != BlankIndex && bestIndex != previousIndex) {
                characters.getOrNull(bestIndex - 1)?.let { character ->
                    text.append(character)
                    confidenceSum += bestProbability
                    characterCount += 1
                }
            }
            previousIndex = bestIndex
        }
        val recognized = text.toString().trim()
        if (recognized.isEmpty()) return null
        return OcrText(
            text = recognized,
            confidence = if (characterCount > 0) confidenceSum / characterCount else 0f,
        )
    }

    private fun Bitmap.crop(bounds: NormalizedRect): Bitmap? {
        val left = (bounds.left * width).toInt().coerceIn(0, width - 1)
        val top = (bounds.top * height).toInt().coerceIn(0, height - 1)
        val right = (bounds.right * width).toInt().coerceIn(left + 1, width)
        val bottom = (bounds.bottom * height).toInt().coerceIn(top + 1, height)
        val rect = Rect(left, top, right, bottom)
        if (rect.width() < 2 || rect.height() < 2) return null
        return Bitmap.createBitmap(this, rect.left, rect.top, rect.width(), rect.height())
    }

    private companion object {
        const val ModelHeight = 48
        const val ModelWidth = 320
        const val MinimumWidth = 32
        const val BlankIndex = 0
    }
}

internal fun loadPaddleCharacters(characterFile: File): List<String> = characterFile
    .inputStream()
    .bufferedReader()
    .use { reader -> Json.decodeFromString<List<String>>(reader.readText()) } + " "
