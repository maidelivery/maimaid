package org.rhythmeta.maimaid.core.ml

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class OnnxSessionFactory(context: Context) {
    private val applicationContext = context.applicationContext
    private val assets = applicationContext.assets
    private val modelDirectory = File(applicationContext.filesDir, "onnx-models").apply {
        mkdirs()
    }
    private val environment = OrtEnvironment.getEnvironment()

    suspend fun create(model: VisionModel): OrtSession = withContext(Dispatchers.IO) {
        val modelFile = modelFile(model)
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        try {
            environment.createSession(modelFile.path, options)
        } finally {
            options.close()
        }
    }

    private fun modelFile(model: VisionModel): File {
        val destination = File(modelDirectory, model.name.lowercase() + ".onnx")
        if (destination.isFile && destination.length() > 0L) return destination

        val temporary = File(modelDirectory, "${destination.name}.download")
        assets.open(model.assetPath).use { input ->
            temporary.outputStream().use { output ->
                input.copyTo(output, bufferSize = 64 * 1_024)
            }
        }
        check(temporary.renameTo(destination) || destination.isFile) {
            "Unable to cache ONNX model: ${model.name}"
        }
        temporary.delete()
        return destination
    }
}
