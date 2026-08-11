package org.rhythmeta.maimaid.core.ml

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OnnxSessionFactory(context: Context) {
    private val assets = context.applicationContext.assets
    private val environment = OrtEnvironment.getEnvironment()

    suspend fun create(model: VisionModel): OrtSession = withContext(Dispatchers.IO) {
        val modelBytes = assets.open(model.assetPath).use { it.readBytes() }
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        try {
            environment.createSession(modelBytes, options)
        } finally {
            options.close()
        }
    }
}
