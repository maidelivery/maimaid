package org.rhythmeta.maimaid.core.data

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ChartFitStore(
    context: Context,
    private val json: Json,
) {
    private val cacheFile = File(context.applicationContext.filesDir, "chart-fit.json")

    suspend fun hasCache(): Boolean = withContext(Dispatchers.IO) {
        cacheFile.isFile && cacheFile.length() > 0L
    }

    suspend fun load(): StaticBundleResponse.ChartFitPayload = withContext(Dispatchers.IO) {
        if (!cacheFile.isFile) return@withContext StaticBundleResponse.ChartFitPayload()
        runCatching {
            json.decodeFromString<StaticBundleResponse.ChartFitPayload>(cacheFile.readText())
        }.getOrDefault(StaticBundleResponse.ChartFitPayload())
    }

    suspend fun save(payload: StaticBundleResponse.ChartFitPayload) = withContext(Dispatchers.IO) {
        val temporary = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        try {
            temporary.writeText(json.encodeToString(StaticBundleResponse.ChartFitPayload.serializer(), payload))
            Files.move(
                temporary.toPath(),
                cacheFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } finally {
            temporary.delete()
        }
    }
}
