package net.krtl.maimaid.scanner.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object CameraFrameBitmapConverter {
    fun toBitmap(imageProxy: ImageProxy): Bitmap? {
        val nv21 = imageProxy.toNv21()
        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )
        val jpegBytes = ByteArrayOutputStream().use { stream ->
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 84, stream)
            stream.toByteArray()
        }
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null
        return bitmap.rotate(imageProxy.imageInfo.rotationDegrees)
    }

    private fun ImageProxy.toNv21(): ByteArray {
        val width = width
        val height = height
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val nv21 = ByteArray(width * height + width * height / 2)

        var outputOffset = 0
        for (row in 0 until height) {
            yPlane.buffer.position(row * yPlane.rowStride)
            yPlane.buffer.get(nv21, outputOffset, width)
            outputOffset += width
        }

        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vuIndex = width * height + row * width + col * 2
                val vIndex = row * vPlane.rowStride + col * vPlane.pixelStride
                val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
                nv21[vuIndex] = vBuffer.get(vIndex)
                nv21[vuIndex + 1] = uBuffer.get(uIndex)
            }
        }
        return nv21
    }

    private fun Bitmap.rotate(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}
