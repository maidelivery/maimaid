package org.rhythmeta.maimaid.core.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.atomic.AtomicInteger

class CoverImageStore(context: Context) {
    private val coversDirectory = File(context.applicationContext.filesDir, "covers").apply {
        mkdirs()
    }

    fun fileFor(imageName: String): File? {
        val safeName = normalizedName(imageName) ?: return null
        val file = File(coversDirectory, safeName)
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    suspend fun downloadMissing(
        imageNames: Iterable<String>,
        onProgress: (completedItems: Int, totalItems: Int) -> Unit = { _, _ -> },
        download: suspend (imageName: String, destination: File) -> Unit,
    ) = coroutineScope {
        val dispatcher = Dispatchers.IO.limitedParallelism(6)
        val pendingNames = imageNames
            .mapNotNull { name -> normalizedName(name) }
            .distinct()
            .filter { fileFor(it) == null }
        val completedCount = AtomicInteger(0)
        onProgress(0, pendingNames.size)
        pendingNames
            .chunked(36)
            .forEach { batch ->
                batch.map { imageName ->
                    async(dispatcher) {
                        val destination = File(coversDirectory, imageName)
                        try {
                            runCatching {
                                download(imageName, destination)
                            }
                        } finally {
                            onProgress(completedCount.incrementAndGet(), pendingNames.size)
                        }
                    }
                }.awaitAll()
            }
    }

    private fun normalizedName(imageName: String): String? {
        val name = imageName.trim()
        if (name.isEmpty() || name.equals("n/a", ignoreCase = true)) return null
        if (File(name).name != name) return null
        return name
    }
}
