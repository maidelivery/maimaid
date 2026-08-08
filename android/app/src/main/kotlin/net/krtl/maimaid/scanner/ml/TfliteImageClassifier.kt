package net.krtl.maimaid.scanner.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

private const val CLASSIFIER_INPUT_SIZE = 640
private const val CLASSIFIER_BYTES_PER_CHANNEL = 4

class TfliteImageClassifier(
    context: Context,
    assetPath: String,
    private val labels: List<String>
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

    fun classify(bitmap: Bitmap): Classification? {
        val input = toInputBuffer(letterbox(bitmap))
        val outputShape = interpreter.getOutputTensor(0).shape()
        val outputSize = outputShape.drop(1).fold(1) { acc, value -> acc * value }
        val output = Array(1) { FloatArray(outputSize) }
        interpreter.run(input, output)

        val scores = output[0]
        val bestIndex = labels.indices.maxByOrNull { scores.getOrNull(it) ?: Float.NEGATIVE_INFINITY } ?: return null
        val bestScore = scores.getOrNull(bestIndex) ?: return null
        return Classification(label = labels[bestIndex], score = bestScore)
    }

    fun close() {
        interpreter.close()
    }

    data class Classification(
        val label: String,
        val score: Float
    )

    private fun toInputBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(CLASSIFIER_INPUT_SIZE * CLASSIFIER_INPUT_SIZE * 3 * CLASSIFIER_BYTES_PER_CHANNEL)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(CLASSIFIER_INPUT_SIZE * CLASSIFIER_INPUT_SIZE)
        bitmap.getPixels(pixels, 0, CLASSIFIER_INPUT_SIZE, 0, 0, CLASSIFIER_INPUT_SIZE, CLASSIFIER_INPUT_SIZE)
        for (pixel in pixels) {
            buffer.putFloat(Color.red(pixel) / 255f)
            buffer.putFloat(Color.green(pixel) / 255f)
            buffer.putFloat(Color.blue(pixel) / 255f)
        }
        buffer.rewind()
        return buffer
    }

    private fun letterbox(bitmap: Bitmap): Bitmap {
        val scale = min(CLASSIFIER_INPUT_SIZE / bitmap.width.toFloat(), CLASSIFIER_INPUT_SIZE / bitmap.height.toFloat())
        val scaledWidth = max(1, (bitmap.width * scale).toInt())
        val scaledHeight = max(1, (bitmap.height * scale).toInt())
        val offsetX = (CLASSIFIER_INPUT_SIZE - scaledWidth) / 2f
        val offsetY = (CLASSIFIER_INPUT_SIZE - scaledHeight) / 2f

        val resized = bitmap.scale(scaledWidth, scaledHeight, true)
        val canvasBitmap = createBitmap(CLASSIFIER_INPUT_SIZE, CLASSIFIER_INPUT_SIZE)
        Canvas(canvasBitmap).apply {
            drawColor(Color.BLACK)
            drawBitmap(resized, offsetX, offsetY, null)
        }
        return canvasBitmap
    }

    private fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(assetPath)
        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            return inputStream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
        }
    }
}
