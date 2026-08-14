package org.rhythmeta.maimaid.core.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.atomic.AtomicInteger

class PresetAvatarImageStore(context: Context) {
    private val imagesDirectory = File(context.applicationContext.filesDir, "preset_avatars").apply {
        mkdirs()
    }

    fun fileFor(id: Int): File? {
        val safeId = id.takeIf { it >= 0 } ?: return null
        val file = File(imagesDirectory, "$safeId.png")
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    suspend fun downloadMissing(
        avatars: Iterable<PresetAvatar>,
        onProgress: (completedItems: Int, totalItems: Int) -> Unit = { _, _ -> },
        download: suspend (id: Int, destination: File) -> Unit,
    ) = coroutineScope {
        val dispatcher = Dispatchers.IO.limitedParallelism(6)
        val pendingIds = avatars
            .map(PresetAvatar::id)
            .filter { it >= 0 }
            .distinct()
            .filter { fileFor(it) == null }
        val completedCount = AtomicInteger(0)
        onProgress(0, pendingIds.size)
        pendingIds
            .chunked(36)
            .forEach { batch ->
                batch.map { id ->
                    async(dispatcher) {
                        val destination = File(imagesDirectory, "$id.png")
                        try {
                            runCatching {
                                download(id, destination)
                            }
                        } finally {
                            onProgress(completedCount.incrementAndGet(), pendingIds.size)
                        }
                    }
                }.awaitAll()
            }
    }
}
