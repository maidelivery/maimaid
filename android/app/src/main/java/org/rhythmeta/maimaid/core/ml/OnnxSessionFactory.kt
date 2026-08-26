package org.rhythmeta.maimaid.core.ml

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OnnxSessionFactory(
    private val modelStore: RemoteModelStore,
) {
    private val environment = OrtEnvironment.getEnvironment()

    suspend fun create(model: VisionModel): OrtSession = withContext(Dispatchers.IO) {
        val modelFile = modelStore.file(model.asset)
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        try {
            environment.createSession(modelFile.path, options)
        } finally {
            options.close()
        }
    }

    suspend fun textCharactersFile() = modelStore.file(ModelAsset.TextCharacters)
}
