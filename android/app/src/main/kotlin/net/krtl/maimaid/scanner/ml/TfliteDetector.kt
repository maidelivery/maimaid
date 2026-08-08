package net.krtl.maimaid.scanner.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import net.krtl.maimaid.scanner.model.ScannerDetection
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val INPUT_SIZE = 640
private const val BYTES_PER_CHANNEL = 4

class TfliteDetector(
    context: Context,
    assetPath: String,
    private val labels: List<String>,
    private val confidenceThreshold: Float
) {
    private val interpreter: Interpreter = Interpreter(
        loadModelFile(context, assetPath),
        Interpreter.Options().apply { setNumThreads(4) }
    )

    val tensorInfo: String = buildString {
        append("input=")
        append(interpreter.getInputTensor(0).shape().joinToString(prefix = "[", postfix = "]"))
        append("/")
        append(interpreter.getInputTensor(0).dataType())
        append(", outputs=")
        for (index in 0 until interpreter.outputTensorCount) {
            if (index > 0) append("; ")
            append(index)
            append("=")
            append(interpreter.getOutputTensor(index).shape().joinToString(prefix = "[", postfix = "]"))
            append("/")
            append(interpreter.getOutputTensor(index).dataType())
        }
    }

    fun detect(bitmap: Bitmap): List<ScannerDetection> {
        val preprocess = letterbox(bitmap)
        val input = toInputBuffer(preprocess.bitmap)
        val outputShape = interpreter.getOutputTensor(0).shape()
        require(outputShape.size == 3) { "Unsupported detector output shape: ${outputShape.joinToString(prefix = "[", postfix = "]")}" }
        val dim1 = outputShape[1]
        val dim2 = outputShape[2]
        require(dim1 > 0 && dim2 > 0) { "Unsupported detector output shape: ${outputShape.joinToString(prefix = "[", postfix = "]")}" }
        val output = Array(1) { Array(dim1) { FloatArray(dim2) } }
        interpreter.run(input, output)

        return output
            .candidateRows(dim1, dim2)
            .mapNotNull { row ->
                val candidate = parseCandidate(row, dim1, dim2) ?: return@mapNotNull null
                if (candidate.confidence < confidenceThreshold || candidate.classId !in labels.indices) {
                    return@mapNotNull null
                }

                val box = preprocess.unletterbox(
                    left = candidate.left,
                    top = candidate.top,
                    right = candidate.right,
                    bottom = candidate.bottom,
                    originalWidth = bitmap.width.toFloat(),
                    originalHeight = bitmap.height.toFloat()
                )

                if (box.width() <= 2f || box.height() <= 2f) {
                    return@mapNotNull null
                }

                ScannerDetection(
                    label = labels[candidate.classId],
                    score = candidate.confidence,
                    left = box.left / bitmap.width,
                    top = box.top / bitmap.height,
                    right = box.right / bitmap.width,
                    bottom = box.bottom / bitmap.height
                )
            }
            .groupBy { it.label }
            .flatMap { (_, detections) -> detections.nonMaxSuppressed().take(3) }
            .sortedByDescending { it.score }
    }

    fun close() {
        interpreter.close()
    }

    private fun toInputBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * BYTES_PER_CHANNEL)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            buffer.putFloat(Color.red(pixel) / 255f)
            buffer.putFloat(Color.green(pixel) / 255f)
            buffer.putFloat(Color.blue(pixel) / 255f)
        }
        buffer.rewind()
        return buffer
    }

    private data class LetterboxResult(
        val bitmap: Bitmap,
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float
    ) {
        fun unletterbox(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            originalWidth: Float,
            originalHeight: Float
        ): RectF {
            val normalizedOutput = max(max(left, top), max(right, bottom)) <= 1.5f
            val modelLeft = if (normalizedOutput) left * INPUT_SIZE else left
            val modelTop = if (normalizedOutput) top * INPUT_SIZE else top
            val modelRight = if (normalizedOutput) right * INPUT_SIZE else right
            val modelBottom = if (normalizedOutput) bottom * INPUT_SIZE else bottom
            val mappedLeft = ((modelLeft - offsetX) / scale).coerceIn(0f, originalWidth)
            val mappedTop = ((modelTop - offsetY) / scale).coerceIn(0f, originalHeight)
            val mappedRight = ((modelRight - offsetX) / scale).coerceIn(0f, originalWidth)
            val mappedBottom = ((modelBottom - offsetY) / scale).coerceIn(0f, originalHeight)
            return RectF(mappedLeft, mappedTop, mappedRight, mappedBottom)
        }
    }

    private fun letterbox(bitmap: Bitmap): LetterboxResult {
        val scale = min(INPUT_SIZE / bitmap.width.toFloat(), INPUT_SIZE / bitmap.height.toFloat())
        val scaledWidth = max(1, (bitmap.width * scale).toInt())
        val scaledHeight = max(1, (bitmap.height * scale).toInt())
        val offsetX = (INPUT_SIZE - scaledWidth) / 2f
        val offsetY = (INPUT_SIZE - scaledHeight) / 2f

        val resized = bitmap.scale(scaledWidth, scaledHeight, true)
        val canvasBitmap = createBitmap(INPUT_SIZE, INPUT_SIZE)
        Canvas(canvasBitmap).apply {
            drawColor(Color.BLACK)
            drawBitmap(resized, offsetX, offsetY, null)
        }
        return LetterboxResult(canvasBitmap, scale, offsetX, offsetY)
    }

    private fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(assetPath)
        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
        }
    }

    private data class ModelCandidate(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val confidence: Float,
        val classId: Int
    )

    private fun Array<Array<FloatArray>>.candidateRows(dim1: Int, dim2: Int): List<FloatArray> {
        val tensor = this[0]
        val maxAttributeCount = max(labels.size + 5, 6)
        val isTransposed = dim1 in 5..maxAttributeCount && dim2 > dim1
        return if (isTransposed) {
            List(dim2) { candidateIndex ->
                FloatArray(dim1) { attributeIndex -> tensor[attributeIndex][candidateIndex] }
            }
        } else {
            tensor.toList()
        }
    }

    private fun parseCandidate(row: FloatArray, dim1: Int, dim2: Int): ModelCandidate? {
        val candidateCount = max(dim1, dim2)
        val attributeCount = row.size
        val looksLikeRawSingleClass = labels.size == 1 && attributeCount == 6 && candidateCount > 1000
        return when {
            looksLikeRawSingleClass -> parseRawYoloCandidate(row, hasObjectness = true)
            attributeCount == 6 -> parseNmsCandidate(row)
            attributeCount >= labels.size + 5 -> parseRawYoloCandidate(row, hasObjectness = true)
            attributeCount >= labels.size + 4 -> parseRawYoloCandidate(row, hasObjectness = false)
            else -> null
        }
    }

    private fun parseNmsCandidate(row: FloatArray): ModelCandidate? {
        val classId = row[5].roundToInt()
        if (classId !in labels.indices) return null
        return ModelCandidate(
            left = row[0],
            top = row[1],
            right = row[2],
            bottom = row[3],
            confidence = row[4],
            classId = classId
        )
    }

    private fun parseRawYoloCandidate(row: FloatArray, hasObjectness: Boolean): ModelCandidate? {
        val classStart = if (hasObjectness) 5 else 4
        if (row.size < classStart + labels.size) return null
        var bestClassId = -1
        var bestClassScore = Float.NEGATIVE_INFINITY
        for (index in labels.indices) {
            val score = row[classStart + index]
            if (score > bestClassScore) {
                bestClassScore = score
                bestClassId = index
            }
        }
        if (bestClassId !in labels.indices) return null

        val objectness = if (hasObjectness) row[4].coerceIn(0f, 1f) else 1f
        val confidence = objectness * bestClassScore.coerceIn(0f, 1f)
        val centerX = row[0]
        val centerY = row[1]
        val width = row[2]
        val height = row[3]
        return ModelCandidate(
            left = centerX - width / 2f,
            top = centerY - height / 2f,
            right = centerX + width / 2f,
            bottom = centerY + height / 2f,
            confidence = confidence,
            classId = bestClassId
        )
    }

    private fun List<ScannerDetection>.nonMaxSuppressed(iouThreshold: Float = 0.45f): List<ScannerDetection> {
        val remaining = sortedByDescending { it.score }.toMutableList()
        val kept = mutableListOf<ScannerDetection>()
        while (remaining.isNotEmpty()) {
            val best = remaining.removeAt(0)
            kept += best
            remaining.removeAll { candidate -> best.iou(candidate) >= iouThreshold }
        }
        return kept
    }

    private fun ScannerDetection.iou(other: ScannerDetection): Float {
        val intersectionLeft = max(left, other.left)
        val intersectionTop = max(top, other.top)
        val intersectionRight = min(right, other.right)
        val intersectionBottom = min(bottom, other.bottom)
        val intersectionWidth = (intersectionRight - intersectionLeft).coerceAtLeast(0f)
        val intersectionHeight = (intersectionBottom - intersectionTop).coerceAtLeast(0f)
        val intersectionArea = intersectionWidth * intersectionHeight
        val unionArea = width * height + other.width * other.height - intersectionArea
        return if (unionArea <= 0f) 0f else intersectionArea / unionArea
    }
}
