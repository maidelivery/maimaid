package org.rhythmeta.maimaid.core.data

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class DanStore(
    context: Context,
    private val json: Json,
) {
    private val cacheFile = File(context.applicationContext.filesDir, "dan-info.json")
    private val serializer = ListSerializer(DanCategory.serializer())

    suspend fun hasCache(): Boolean = withContext(Dispatchers.IO) {
        cacheFile.isFile && cacheFile.length() > 0L
    }

    suspend fun load(): List<DanCategory> = withContext(Dispatchers.IO) {
        if (!cacheFile.isFile) return@withContext emptyList()
        runCatching { json.decodeFromString(serializer, cacheFile.readText()) }
            .getOrDefault(emptyList())
    }

    suspend fun save(categories: List<DanCategory>) = withContext(Dispatchers.IO) {
        val temporary = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        try {
            temporary.writeText(json.encodeToString(serializer, categories))
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
