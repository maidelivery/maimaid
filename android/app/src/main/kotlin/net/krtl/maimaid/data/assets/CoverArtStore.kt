package net.krtl.maimaid.data.assets

import android.content.Context
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object CoverArtStore {
    private const val COVER_BASE_URL = "https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover/"
    private const val COVER_CACHE_KEY_PREFIX = "cover-art/"

    fun coverUrl(imageName: String): String = COVER_BASE_URL + imageName.trim()

    fun normalizeImageName(imageName: String): String? {
        val cleanName = imageName.trim()
        return cleanName.takeIf(::isValidImageName)
    }

    fun coverCacheKey(imageName: String): String? =
        normalizeImageName(imageName)?.let { COVER_CACHE_KEY_PREFIX + it.lowercase() }

    fun coversDirectory(context: Context): File {
        val directory = File(context.filesDir, "Covers")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }

    fun coverFile(context: Context, imageName: String): File? {
        val cleanName = imageName.trim()
        if (!isValidImageName(cleanName)) return null
        return File(coversDirectory(context), cleanName)
    }

    fun coverModel(context: Context, imageName: String): Any? {
        val cleanName = normalizeImageName(imageName) ?: return null
        val localFile = coverFile(context, cleanName)
        return if (localFile != null && localFile.exists()) localFile else coverUrl(cleanName)
    }

    fun buildImageRequest(
        context: Context,
        imageName: String,
        highQuality: Boolean = false,
        enableCrossfade: Boolean = false
    ): ImageRequest? {
        val cleanName = normalizeImageName(imageName) ?: return null
        val cacheKey = coverCacheKey(cleanName) ?: return null
        return ImageRequest.Builder(context)
            .data(coverModel(context, cleanName))
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .precision(if (highQuality) Precision.EXACT else Precision.INEXACT)
            .crossfade(enableCrossfade)
            .build()
    }

    fun coverExists(context: Context, imageName: String): Boolean =
        coverFile(context, imageName)?.exists() == true

    suspend fun prefetchMissingCovers(
        context: Context,
        okHttpClient: OkHttpClient,
        imageNames: List<String>,
        onProgress: suspend (completed: Int, total: Int, downloadedBytes: Long, totalBytes: Long) -> Unit =
            { _, _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val targets = imageNames
            .map(String::trim)
            .filter(::isValidImageName)
            .distinct()
            .filterNot { coverExists(context, it) }

        if (targets.isEmpty()) return@withContext

        var completed = 0
        val total = targets.size
        var downloadedBytes = 0L
        var totalBytes = 0L

        coroutineScope {
            targets.chunked(24).forEach { batch ->
                batch.forEach { imageName ->
                    launch {
                        val result = runCatching {
                            downloadCover(context, okHttpClient, imageName)
                        }.getOrDefault(DownloadResult.empty())
                        synchronized(this@CoverArtStore) {
                            completed += 1
                            downloadedBytes += result.downloadedBytes
                            totalBytes += result.totalBytes
                        }
                        onProgress(completed, total, downloadedBytes, totalBytes)
                    }
                }
            }
        }
    }

    private fun downloadCover(context: Context, okHttpClient: OkHttpClient, imageName: String): DownloadResult {
        val destination = coverFile(context, imageName) ?: return DownloadResult.empty()
        if (destination.exists()) return DownloadResult.empty()

        val request = Request.Builder()
            .url(coverUrl(imageName))
            .header("User-Agent", "maimaid-android")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return DownloadResult.empty()
            val body = response.body
            val contentLength = body.contentLength().coerceAtLeast(0L)
            val bytes = body.bytes()
            if (bytes.isEmpty()) return DownloadResult.empty()
            destination.outputStream().use { it.write(bytes) }
            val downloaded = bytes.size.toLong()
            val expected = if (contentLength > 0L) contentLength else downloaded
            return DownloadResult(downloadedBytes = downloaded, totalBytes = expected)
        }
    }

    private data class DownloadResult(
        val downloadedBytes: Long,
        val totalBytes: Long
    ) {
        companion object {
            fun empty(): DownloadResult = DownloadResult(downloadedBytes = 0L, totalBytes = 0L)
        }
    }

    private fun isValidImageName(imageName: String): Boolean {
        if (imageName.isBlank()) return false
        val lower = imageName.lowercase()
        return lower != "n/a"
    }
}
